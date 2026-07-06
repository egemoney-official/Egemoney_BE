# RAG 기반 퀴즈 생성 시스템 구축 회고록

> **프로젝트**: 이게머니(igemoney) — 금융문맹 해소를 위한 금융 퀴즈 서비스
> **작업 기간**: 2026.07 (계획 수립 → 구현 → PR 머지 완료)
> **작업 브랜치**: `feature/quiz-generation` → PR #10, #11, #12, #13으로 main 병합
> **작성 목적**: RAG 시스템의 전체 데이터 흐름과 Phase별 작업 내용을 팀원 누구나 코드를 따라가며 이해할 수 있도록 기록

---

## 1. 개요

### 1.1 문제 정의

관리자가 퀴즈를 출제할 때 겪는 두 가지 문제에서 출발했다.

1. **출제 품질**: LLM에게 "금융 퀴즈 만들어줘"라고만 하면 근거 없는 내용(환각)이나 부정확한 수치가 섞인 문제가 나올 수 있다.
2. **중복 출제**: 기존에 있는 퀴즈와 사실상 같은 문제를 또 만들 수 있다. 기존의 Jaccard(단어 겹침) 방식은 "단리와 복리의 차이는?"과 "복리가 단리와 다른 점은?"을 다른 문제로 판단한다.

### 1.2 해결 접근: RAG (Retrieval-Augmented Generation)

- **출제 품질** → 신뢰할 수 있는 금융 지식 문서를 미리 벡터 DB에 저장해 두고, 퀴즈 생성 시 관련 문서를 **검색(Retrieval)** 해서 LLM에게 "이 근거로만 출제하라"고 **증강(Augmented)** 된 프롬프트로 **생성(Generation)** 시킨다.
- **중복 출제** → 퀴즈 제목을 임베딩 벡터로 변환해 **의미 기반 유사도**로 비교한다. 단어가 달라도 뜻이 같으면 잡아낸다.

### 1.3 기술 스택

| 구성 요소 | 선택 | 역할 |
|---|---|---|
| 원본 DB | MySQL (기존) | 퀴즈·토픽·사용자 등 서비스 데이터의 원본(source of truth) |
| 벡터 DB | Supabase (PostgreSQL + pgvector) | 지식 청크·퀴즈 임베딩 저장, 코사인 유사도 검색 |
| 임베딩 모델 | Voyage AI `voyage-3.5` (1024차원) | 텍스트 → 벡터 변환 |
| LLM | Anthropic Claude `claude-opus-4-8` (공식 Java SDK) | 퀴즈 초안 생성 |
| 프레임워크 | Spring Boot 3.5.5 / Java 21 | 기존 스택 유지, Spring AI 등 RAG 프레임워크 미사용 |

### 1.4 차별화 포인트: 청킹 전략 실험

RAG 품질은 문서를 어떻게 쪼개느냐(청킹)에 크게 좌우된다. 청킹 방법을 하나로 고정하지 않고 **6가지 전략을 모두 구현한 뒤, 골든 질의셋 60개로 검색 품질(Hit@k, Recall, MRR)을 정량 비교해서 최적 전략을 선정**하는 실험 체계를 함께 구축했다.

---

## 2. 아키텍처 총람

### 2.1 이중 DB 구조

```
┌─────────────────────────┐          ┌──────────────────────────────┐
│  MySQL (원본)            │          │  Supabase pgvector (인덱스)   │
│                         │          │                              │
│  quiz_topic             │─topic_id─▶  document_chunks             │
│  quiz                   │─quiz_id──▶  quiz_embeddings             │
│  quiz_select            │          │                              │
│  quiz_subjective        │          │  ※ FK 없음, ID 값만 저장       │
│  user, attempt ...      │          │  ※ 언제든 삭제 후 재구축 가능    │
└─────────────────────────┘          └──────────────────────────────┘
```

**핵심 원칙**: MySQL이 항상 원본이다. Supabase는 검색을 위한 파생 인덱스일 뿐이며, 두 DB 간 트랜잭션이나 FK는 존재하지 않는다. Supabase 쪽 데이터가 전부 사라져도 `재적재(reindex)` 한 번으로 복구된다. 그래서 벡터 저장 실패는 서비스 실패로 이어지지 않는다(§3.3 참고).

### 2.2 오프라인 / 온라인 파이프라인 분리

```
[오프라인 - 지식 적재]  문서가 바뀔 때만 실행
  data/knowledge/*.md → 파싱 → 청킹(6전략) → 임베딩 → document_chunks 저장

[오프라인 - 실험]  전략 선정할 때 실행
  data/eval/retrieval-eval.yml(질의 60개) → 전략별 검색 → 지표 산출 → 리포트

[온라인 - 퀴즈 생성]  관리자가 API 호출할 때마다 실행
  요청 → 질의 임베딩 → 청크 검색 → 프롬프트 조립 → Claude 생성 → 유사도 검사 → 응답

[온라인 - 임베딩 동기화]  퀴즈 저장/삭제 시 자동 실행
  MySQL 커밋 완료 → 이벤트 → 퀴즈 제목 임베딩 → quiz_embeddings upsert/delete
```

온라인 단계에서는 문서를 다시 파싱하거나 청킹하지 않는다. 이미 저장된 벡터를 **검색만** 한다.

### 2.3 mock / rag 모드 스위치

시스템 전체가 두 개의 설정 스위치로 제어된다. **기본값이 전부 꺼짐**이라서, 환경변수 없이 배포해도 기존 서비스는 RAG 코드의 존재를 모른다.

| 환경변수 | 기본값 | 켜면 활성화되는 것 |
|---|---|---|
| `VECTOR_STORE_ENABLED` | `false` | 벡터 DataSource, 임베딩 클라이언트, 벡터 리포지토리, 지식 관리 API, 동기화 리스너 |
| `QUIZ_GENERATION_MODE` | `mock` | `rag`로 설정 시: RagQuizGenerationService, VectorQuizSimilarityService, Claude 클라이언트 (mock 구현체는 비활성화) |
| `RAG_CHUNKING_STRATEGY` | `structure` | 온라인 검색에 사용할 청킹 전략 (실험 후 확정값으로 변경) |

구현 방식: `@ConditionalOnProperty`. 예를 들어 `MockQuizGenerationService`는 `matchIfMissing=true`라서 설정이 없으면 자동 선택되고, `RagQuizGenerationService`는 `quiz.generation.mode=rag`일 때만 빈으로 등록된다. 같은 `QuizGenerationService` 인터페이스를 구현하므로 **호출하는 쪽(컨트롤러/오케스트레이터)은 어느 구현체인지 모른다.**

---

## 3. 데이터 흐름 상세 (함수·자료구조 레벨)

> 이 장은 코드를 직접 열어 따라가며 읽는 것을 전제로 작성했다. 패키지 경로는 `com.igemoney.igemoney_BE` 하위 기준.

### 3.1 파이프라인 A: 지식 문서 적재 (Ingestion)

**진입점** — 둘 중 하나:
- CLI: `./gradlew bootRun --args='--ingest-knowledge --strategies=structure,hybrid'` → `knowledge.KnowledgeIngestionRunner`
- API: `POST /api/admin/knowledge/reindex` (body: `{"strategies": [...]}`, 생략 시 6개 전체) → `knowledge.controller.KnowledgeAdminController.reindex()`

**단계별 흐름과 데이터 변환**:

```
 (1) data/knowledge/economic-basic__interest-and-prices.md   ← 사람이 작성한 md 원본
      │  KnowledgeDocumentParser.parse(Path)
      │    - front-matter(--- source / topic ---) 추출
      │    - "## 헤딩" 정규식으로 본문을 섹션 분할
      ▼
 (2) ParsedDocument                                          ← record
      { sourceFile: "economic-basic__interest-and-prices.md",
        source: "한국은행 경제금융용어 700선 ...",
        topicSlug: "경제기초",
        sections: [ Section{heading:"단리와 복리", content:"단리는..."}, ... ] }
      │  ChunkingStrategy.chunk(document)     ← 6개 구현체 중 하나 (전략마다 반복)
      ▼
 (3) List<Chunk>                                             ← record { content, heading }
      │  KnowledgeIngestionService.ingestStrategy()
      │    - topicSlug → MySQL QuizTopic ID 매핑 (TOPIC_ALIASES: "economic-basic"→"경제기초",
      │      "common"/"공통" → topicId=null(공통 지식))
      │    - EmbeddingClient.embedAllDocuments(List<String>)  ← Voyage API 배치 호출(128개씩)
      ▼
 (4) List<DocumentChunkRecord>                               ← record
      { chunkingStrategy:"structure", topicId:1,
        sourceFile:"...", heading:"단리와 복리",
        content:"단리는...", source:"한국은행...",
        charLength:512, embedding: float[1024] }
      │  VectorStoreRepository.insertChunks(records)          ← JDBC 배치 insert
      ▼
 (5) Supabase document_chunks 테이블 (전략별로 공존, chunking_strategy 컬럼으로 구분)
```

**멱등성**: `ingestStrategy()`는 항상 `deleteChunks(strategy, null)`로 해당 전략의 기존 청크를 전부 지우고 다시 넣는다. 같은 명령을 몇 번 실행해도 결과가 같다.

**결과 요약**: `KnowledgeIngestionSummary` record로 전략별 청크 수 / 평균·최대 길이 / 임베딩 배치 호출 수가 반환되고 로그로도 남는다.

### 3.2 파이프라인 B: RAG 퀴즈 생성 (온라인)

**진입점**: `POST /api/admin/quizzes/generate` → `AdminQuizController` → `DefaultAdminQuizGenerationService.generate()`
(이 오케스트레이터는 mock 시절 그대로다. ① 생성 서비스 호출 → ② 유사도 서비스 호출 → ③ 응답 조립)

**① 생성: `RagQuizGenerationService.generateDrafts(QuizGenerateRequest)`**

```
 (1) QuizGenerateRequest { topicId, questionType, difficultyLevel, count, additionalPrompt }
      │  검증: 토픽 존재(TopicNotFoundException), 유형/난이도 enum 파싱, count 1~5 제한
      │       → mock 구현과 동일한 규칙 (기존 API 계약 유지)
      ▼
 (2) 질의 텍스트 = "{토픽명} {additionalPrompt}"        예: "경제기초 복리 개념"
      │  EmbeddingClient.embedQuery(queryText)          ← Voyage, input_type="query"
      ▼
 (3) float[1024] 질의 벡터
      │  VectorStoreRepository.searchChunks(
      │      strategy = rag.retrieval.strategy 설정값,   ← 실험으로 확정한 전략
      │      queryEmbedding, topicId, topK = 6)
      │  SQL: ORDER BY embedding <=> ?::vector (코사인 거리) + 토픽 일치 or 공통(null) 청크
      ▼
 (4) List<RetrievedChunk> { content, sourceFile, heading, similarity }
      │  ※ 0건이어도 실패하지 않음: "no-context" 경고 로그 + 시스템 프롬프트가
      │    "일반 지식 사용, 추측성 수치 금지" 모드로 전환됨 (buildSystemPrompt(noContext))
      │
      │  buildUserPrompt(): 토픽/유형 규칙/난이도/개수 + 검색 컨텍스트를
      │  "[1] sourceFile=..., heading=..., similarity=0.87\n본문..." 형식으로 나열
      ▼
 (5) QuizLlmClient.generate(systemPrompt, userPrompt)
      │  구현체 AnthropicQuizLlmClient:
      │    - 모델 claude-opus-4-8, maxTokens 8192, adaptive thinking
      │    - structured outputs: outputConfig(QuizDraftBatch.class)
      │      → SDK가 record 구조에서 JSON 스키마를 만들고 응답 파싱까지 수행
      ▼
 (6) QuizDraftBatch { quizzes: [ QuizDraftItem {
        questionTitle, questionType, difficultyLevel, explanation,
        choices: [ QuizChoiceDraft{sequence, content, answer} ],
        subjectiveAnswers: [String] } ] }
      │  mapDrafts(): LLM 응답을 신뢰하지 않고 재검증
      │    - 요청한 유형/난이도와 일치? 개수 충분? (부족하면 IllegalStateException)
      │    - OX: 보기 2개 + O/X 라벨 존재,  객관식: 보기 4개,  정답 정확히 1개
      │    - 주관식: 정답 문자열 1개 이상, 첫 번째가 대표 정답
      │    - sequence 기준 정렬 후 1부터 재부여
      ▼
 (7) List<GeneratedQuizDraft>   ← mock 시절과 완전히 동일한 record (API 계약 불변의 핵심)
```

**② 유사도 검사: `VectorQuizSimilarityService.analyzeCandidates(drafts)`**

```
 각 초안에 대해:
 (1) embedQuery(draft.questionTitle())                        ← 제목만 임베딩
 (2) VectorStoreRepository.searchSimilarQuizzes(embedding, topicId, top3)
      → List<SimilarQuizHit> { quizId, questionTitle, similarity }
 (3) similarity >= 0.80 만 후보로 필터
 (4) QuizRepository.findAllById(quizIds)                      ← MySQL에서 실제 퀴즈 배치 조회
 (5) SimilarQuizResponse.from(quiz, similarity, reason) 매핑
 (6) exactDuplicate = (similarity >= 0.95 존재 여부)
      maxSimilarityScore = 후보 중 최댓값
 → GeneratedQuizCandidateResponse { draft, similarQuizzes, exactDuplicate, maxSimilarityScore }
```

임계값 의미: **0.80** 이상 = "유사 후보로 관리자에게 보여줌", **0.95** 이상 = "사실상 중복으로 판정". (실데이터 캘리브레이션 예정 — §8)

### 3.3 파이프라인 C: 퀴즈 임베딩 동기화

퀴즈가 MySQL에 저장/삭제될 때 `quiz_embeddings`를 따라 맞추는 흐름. **"커밋 후 실행 + 실패 무시"** 가 설계의 전부다.

```
 QuizCreateService.create() / QuizAdminService.delete()
      │  MySQL 트랜잭션 안에서 ApplicationEventPublisher.publishEvent(
      │      QuizEmbeddingUpsertRequestedEvent(quizId))    ← 이벤트만 발행, 벡터 작업 안 함
      ▼  (MySQL 커밋 완료 후)
 QuizEmbeddingEventListener
      │  @TransactionalEventListener(phase = AFTER_COMMIT)  ← 커밋 성공시에만 실행
      │  try { QuizEmbeddingSyncService.upsertById(quizId) }
      │  catch (RuntimeException e) { log.warn(...) }       ← 실패해도 예외 전파 없음
      ▼
 QuizEmbeddingSyncService.upsert(quiz)
      │  embedDocument(quiz.questionTitle) → upsertQuizEmbedding(quizId, topicId, title, vector)
      ▼
 Supabase quiz_embeddings (PK=quiz_id, ON CONFLICT UPDATE)
```

- 왜 AFTER_COMMIT인가: 커밋 전에 임베딩을 저장하면, MySQL 롤백 시 존재하지 않는 퀴즈의 벡터가 남는다.
- 왜 실패를 무시하는가: Supabase/Voyage 장애가 퀴즈 저장을 막으면 안 된다(§2.1 원칙). 누락분은 `POST /api/admin/knowledge/reindex-quizzes` (`QuizEmbeddingSyncService.reindexAll()`: 전체 삭제 → MySQL 전체 조회 → 배치 임베딩 → 재삽입)로 복구한다.

### 3.4 파이프라인 D: 청킹 전략 평가 실험

**진입점**: CLI `--evaluate-chunking` (`KnowledgeEvaluationRunner`) 또는 `POST /api/admin/knowledge/evaluate` (마크다운을 그대로 응답)

```
 (1) data/eval/retrieval-eval.yml            ← 골든 질의셋 60개
      - query: "은행이 파산하면 내 예금은 얼마까지 보호받나?"
        relevantSourceFile: "living-economy__deposit-savings.md"
        relevantHeading: "예금자보호제도"
        topic: "생활 경제"
      │  RetrievalEvaluationDatasetLoader → List<RetrievalEvaluationQuery>
      ▼
 (2) RetrievalEvaluationService.evaluate(strategies)
      - 질의당 embedQuery() 1회만 수행, 모든 전략에 재사용 (공정 비교 + 비용 절약)
      - 전략별로 searchChunks(topK=5) → 정답 판정:
          청크.sourceFile == 라벨.relevantSourceFile
          (+ 청크에 heading이 있으면 relevantHeading까지 일치해야 정답
             — fixed 계열은 heading이 null이라 파일 일치만 적용)
      ▼
 (3) 전략별 RetrievalStrategyMetrics { Hit@1, Hit@3, Recall@5, MRR@5, 청크수, 평균길이 }
      │  지표 뜻:
      │   Hit@1  = 1등 검색 결과가 정답인 질의 비율
      │   Hit@3  = 상위 3개 안에 정답이 있는 비율
      │   Recall@5 = 상위 5개 안에 정답이 있는 비율
      │   MRR@5  = 정답의 순위 역수 평균 (1등이면 1.0, 2등이면 0.5 ...)
      ▼
 (4) RetrievalEvaluationReport.markdown()
      → docs/experiments/chunking-eval-{일시}.md 저장 (전략 비교표 + 질의별 상세 + 결론)
```

---

## 4. Phase별 작업 기록

전체 구현은 `docs/RAG_IMPLEMENTATION_PLAN.md`(AI 에이전트 실행용 작업지시서)를 기준으로 9단계로 진행했다. 계획서를 Codex(AI 코딩 에이전트)에 입력해 Phase 단위로 구현·검증·커밋하는 방식으로 작업했다.

| Phase | 커밋 | 작업 내용 | 핵심 산출물 |
|---|---|---|---|
| 1 | `e298fbe` | 벡터 DB 연결 계층 | `VectorDataSourceConfig` — vector.enabled 조건부, HikariCP(pool 3), `vectorJdbcTemplate`. JPA는 MySQL 전용 유지 |
| 2 | `2fa4f3b` | 임베딩 클라이언트 | `EmbeddingClient` 인터페이스 + `VoyageEmbeddingClient` (document/query 타입 구분, 128개 배치 분할, 재시도) |
| 3 | `972d7da` | 벡터 저장소 | `VectorStoreRepository` + `JdbcVectorStoreRepository` (pgvector `<=>` 검색, `?::vector` 캐스팅, 전략 스코프) |
| 4 | `0e32dff`, `0ecaaf3` | 청킹 전략 6종 | `ChunkingStrategy` + fixed / fixed_overlap / sentence_window / semantic / structure / hybrid. 공통 상수(500/1000/100/100자)와 `SentenceSplitter` 공유 |
| 5 | `ce230c8`, `b44250c` | 지식 적재 | `KnowledgeDocumentParser`, `KnowledgeIngestionService` (전략별 멱등 재적재), CLI 러너, 관리자 API |
| 6 | `98fa341` | 평가 실험 | `RetrievalEvaluationService` (Hit/Recall/MRR), 골든 질의셋, 마크다운 리포트 생성 |
| 7 | `360ccb6` | RAG 생성 | `RagQuizGenerationService`, `QuizLlmClient`/`AnthropicQuizLlmClient` (structured outputs), mock↔rag 모드 전환 |
| 8 | `2a9df7b`, `96f2c83` | 유사도·동기화 | `VectorQuizSimilarityService` (0.80/0.95), `QuizEmbeddingSyncService` + AFTER_COMMIT 리스너, 퀴즈 리인덱스 API |
| 9 | `9b41e66` 외 | 마무리 | `RAG_SETUP.md`, `vector-schema.sql`, `.env.example`, 질의셋 60개 확장(`93f132e`) |

병행 작업 (Claude Code 담당):
- 지식 코퍼스 확장 (`f54c8e6`): 5개 샘플 → **30개 파일, 144개 개념 섹션, 약 190KB**. 한국은행 『경제금융용어 700선』, 금감원 『대학생을 위한 실용금융』·『금융꿀팁 200선』의 주제 체계를 따라 재서술. 예금자보호 1억 원 상향(2025.9) 등 최신 제도 반영, 자주 바뀌는 세율·수치는 구조 위주로 서술.
- 구현 계획서(`8109a16`), PR 4건 작성·머지 관리.

### 테스트 전략

모든 단계에서 **외부 API를 호출하는 코드는 인터페이스(`EmbeddingClient`, `QuizLlmClient`, `VectorStoreRepository`) 뒤에 격리**했고, 테스트는 전부 스텁으로 수행했다. 결과:
- CI에서 API 키·외부 네트워크 없이 전체 테스트 통과
- 청킹 경계 조건, 지표 계산 수식, LLM 응답 검증 로직, 동기화 실패 격리를 각각 단위 테스트로 검증 (테스트 코드만 약 2,000줄)
- mock 모드 기본값 덕분에 기존 통합 테스트가 수정 없이 그대로 통과 → 회귀 없음을 증명

---

## 5. 청킹 전략 실험

### 5.1 왜 실험이 필요한가

청크가 너무 작으면 문맥이 끊기고, 너무 크면 관련 없는 내용이 섞여 검색 정확도와 LLM 컨텍스트 품질이 모두 떨어진다. "무엇이 최적인가"는 코퍼스의 성격(우리는 헤딩 구조가 있는 한국어 금융 교육 문서)에 따라 다르므로, 이론이 아니라 **우리 데이터에서의 측정**으로 답해야 한다.

### 5.2 전략 6종 요약

| 전략 | 분할 기준 | 특징 / 가설 |
|---|---|---|
| `fixed` | 500자 기계적 절단 | 베이스라인. 문장·구조 무시 → 최저 성능 예상 |
| `fixed_overlap` | 500자 + 100자 중복 | 경계에서 잘린 문맥을 중복으로 보완 |
| `sentence_window` | 문장 단위로 500자까지 채움 + 마지막 문장 공유 | 문장 경계 보장판 오버랩 |
| `semantic` | 인접 문장 임베딩 유사도가 `평균−1σ` 미만인 지점 | 주제가 바뀌는 곳에서 분할. 임베딩 비용 추가 |
| `structure` | `##` 헤딩 단위 (초과 시 문단 재분할) | 문서 구조 활용. 우리 코퍼스와 가장 궁합이 좋을 것으로 예상 |
| `hybrid` | structure 1차 → 1000자 초과 섹션만 semantic 2차 | 구조+의미 결합 |

**공정 비교 장치**: 6개 전략 모두 동일한 `SentenceSplitter`, 동일한 크기 상수(TARGET 500 / MAX 1000 / MIN 100 / OVERLAP 100), 동일한 임베딩 모델, 동일한 top-k를 사용한다. MIN 미만 청크는 인접 청크와 병합하는 규칙도 공통. 이것이 다르면 측정 결과가 전략 차이인지 파라미터 차이인지 구분할 수 없다.

### 5.3 평가 설계

- **골든 질의셋**: 60개 (문서 30개 기준, 문서당 2개). 각 질의에 정답 문서·헤딩 라벨.
- **지표**: Hit@1, Hit@3, Recall@5, MRR@5 (§3.4 참고) + 전략별 청크 수·평균 길이(비용 지표)
- **실행**: `--ingest-knowledge`(6전략 적재) → `--evaluate-chunking` → 리포트의 최고 전략을 `RAG_CHUNKING_STRATEGY`로 확정

### 5.4 실험 결과

> ⏳ **(작성 예정)** 실환경 실험 후 `docs/experiments/chunking-eval-*.md` 리포트를 바탕으로 채운다.
>
> 기록할 것: 전략별 지표 비교표 / 최종 선정 전략과 근거 / 예상과 달랐던 점 / 질의 유형별 강약점 분석

---

## 6. 주요 의사결정 기록 (ADR)

**D1. 벡터 DB로 Supabase(pgvector)를 선택한 이유**
전용 벡터 DB(Qdrant, Milvus)는 별도 인프라와 새 API 학습이 필요하다. Supabase는 관리형 PostgreSQL이라 SQL 그대로 쓸 수 있고, 무료 티어가 있으며, pgvector의 HNSW 인덱스로 이 규모(수백~수천 청크)에는 성능이 충분하다. MySQL 9의 VECTOR 타입은 커뮤니티 에디션에 ANN 인덱스가 없어 제외.

**D2. Supabase 접근에 JPA가 아닌 JdbcTemplate만 쓰는 이유**
이중 JPA(EntityManager 2개)는 설정 복잡도가 크고, 벡터 테이블은 엔티티 생명주기 관리가 필요 없는 단순 insert/select다. `vectorJdbcTemplate` 하나로 격리하면 기존 JPA(MySQL)에 어떤 영향도 주지 않는다.

**D3. Spring AI / LangChain4j를 도입하지 않은 이유**
추상화 계층이 얹히면 디버깅이 어려워지고 학습 비용이 든다. 이 프로젝트의 RAG는 "임베딩 호출 + SQL 검색 + 프롬프트 조립" 세 조각이라 직접 구현이 오히려 단순하며, LLM 호출은 공식 Anthropic SDK의 structured outputs가 스키마 생성·파싱까지 해결해 준다.

**D4. mock을 기본값으로 유지한 이유**
API 키 없는 환경(CI, 팀원 로컬)에서 빌드·테스트가 항상 통과해야 하고, RAG 장애 시 mock으로 즉시 되돌릴 수 있는 운영 스위치가 필요했다. `matchIfMissing=true`로 "설정을 모르면 안전한 쪽"이 되도록 했다.

**D5. 임베딩 동기화를 AFTER_COMMIT + 실패 무시로 설계한 이유**
벡터 DB는 재구축 가능한 인덱스이므로(D1의 전제), 동기화 실패는 "나중에 리인덱스로 복구할 수 있는 지연"일 뿐 서비스 오류가 아니다. 반대로 동기화를 트랜잭션에 묶으면 Supabase 장애가 퀴즈 저장 실패로 번진다. 가용성을 선택했다.

**D6. 임베딩 모델로 Voyage `voyage-3.5`를 선택한 이유**
Anthropic은 임베딩 API가 없어 별도 선택이 필요했다. Voyage는 Anthropic 공식 추천 파트너이고, 한국어 포함 다국어 성능이 좋으며, 무료 크레딧이 넉넉하다. 문서용(`input_type=document`)과 질의용(`query`) 임베딩을 구분하는 기능도 검색 정확도에 유리하다. **주의: 모델/차원(1024)을 바꾸면 전체 리인덱스가 필요**하므로 초기에 고정했다.

---

## 7. 트러블슈팅 & 배운 점

**7.1 Stacked PR 머지 사고**
PR을 #10(인프라, base=main) → #11(청킹, base=#10 브랜치) → #12(생성, base=#11 브랜치)로 쌓아 올렸는데, 순차 머지(머지 → base 브랜치 삭제 → 다음 PR의 base 자동 변경 확인) 대신 세 개를 동시에 머지했다. 결과: #11, #12가 main이 아닌 **중간 브랜치로 흡수**되어 main에 반영되지 않았다. 커밋은 유실되지 않았기에 전체를 담은 브랜치를 main으로 보내는 마무리 PR #13으로 해결했다.
→ **배운 점**: stacked PR은 반드시 위에서부터 하나씩, base 재지정을 확인하며 머지한다. 또한 squash 머지는 커밋 해시를 바꿔 후속 PR에 중복 diff를 만들므로 stacked 구조에서는 merge commit을 쓴다.

**7.2 AI 에이전트 협업 방식**
계획서(`RAG_IMPLEMENTATION_PLAN.md`)를 "에이전트가 그대로 실행 가능한 작업지시서" 수준으로 작성한 것이 주효했다. 특히 효과가 있었던 요소: ① Phase마다 "완료 기준"을 명시해 에이전트가 자가 검증하게 한 것 ② 아키텍처 원칙과 "하지 말 것" 목록으로 흔한 이탈(프레임워크 추가, 계약 변경, 시크릿 커밋)을 사전 차단한 것 ③ SDK 좌표와 코드 스니펫을 계획서에 박아 환각을 방지한 것.

**7.3 코퍼스 설계에서의 교훈**
- 지식 문서를 토픽당 1개 대형 파일이 아니라 주제별 소형 파일(30개)로 나눈 것은 평가 때문이다. 정답 라벨이 `sourceFile` 단위이므로, 파일이 잘게 나뉠수록 "정확히 그 주제 문서를 찾았는가"를 엄격히 채점할 수 있다.
- front-matter의 `topic`을 MySQL 토픽명과 일치시키는 규약 덕분에 코드 수정 없이 문서만 추가해도 적재가 동작한다.
- 자주 바뀌는 수치(세율, 한도)는 구조 설명 위주로 서술해 문서 노화를 늦췄다. 단 예금자보호 1억 원처럼 퀴즈 소재로 중요한 제도 변경은 명시했다.

**7.4 (작성 예정) 실환경 실험에서 만난 문제들**
> ⏳ Supabase 연결, Voyage 배치, 프롬프트 튜닝 과정의 실제 트러블을 여기에 기록한다.

---

## 8. 남은 과제

| 과제 | 내용 | 상태 |
|---|---|---|
| 실환경 스모크 테스트 | Supabase 연결 → 6전략 적재 → 청크 통계 검증 | 예정 |
| 청킹 실험 실행 | `--evaluate-chunking` → 최적 전략 확정 → §5.4 작성 | 예정 |
| 생성 품질 검증 | 토픽 5 × 유형 3 실생성, 환각·오답보기 품질 확인, 프롬프트 튜닝 | 예정 |
| 유사도 임계값 캘리브레이션 | 0.80/0.95를 실데이터로 검증·조정 | 예정 |
| 운영 관측성 | 생성 1회당 토큰·비용·소요시간 로깅 | 미착수 |
| 평가 고도화 | 질의셋 확대, 검색 실패 케이스 분석 | 미착수 |

---

## 부록 A. 설정 레퍼런스

```yaml
# application.yml (전부 환경변수 주입, 기본값은 "꺼짐")
vector:
  enabled: ${VECTOR_STORE_ENABLED:false}
  datasource:
    url: ${VECTOR_DB_URL:}            # jdbc:postgresql://...pooler.supabase.com:5432/postgres?sslmode=require
    username: ${VECTOR_DB_USERNAME:}
    password: ${VECTOR_DB_PASSWORD:}
embedding:
  voyage:
    api-key: ${VOYAGE_API_KEY:}
    model: voyage-3.5                  # 1024차원, 변경 시 전체 리인덱스 필요
quiz:
  generation:
    mode: ${QUIZ_GENERATION_MODE:mock} # mock | rag
rag:
  retrieval:
    strategy: ${RAG_CHUNKING_STRATEGY:structure}
    top-k: 6
# 별도 환경변수: ANTHROPIC_API_KEY (Anthropic SDK가 자동 인식)
```

## 부록 B. 실행 커맨드 치트시트

```bash
# 지식 문서 적재 (6개 전략 전체 / 특정 전략만)
./gradlew bootRun --args='--ingest-knowledge'
./gradlew bootRun --args='--ingest-knowledge --strategies=structure,hybrid'

# 청킹 전략 평가 → docs/experiments/chunking-eval-*.md 생성
./gradlew bootRun --args='--evaluate-chunking'

# RAG 모드로 서비스 기동
VECTOR_STORE_ENABLED=true QUIZ_GENERATION_MODE=rag ./gradlew bootRun

# 관리자 API (전부 @Authenticated + vector.enabled=true 필요)
POST /api/admin/knowledge/reindex           # body: {"strategies": [...]} 생략 시 전체
POST /api/admin/knowledge/evaluate          # 응답: 마크다운 리포트
POST /api/admin/knowledge/reindex-quizzes   # 퀴즈 임베딩 전체 재구축
POST /api/admin/quizzes/generate            # 퀴즈 생성 (mock/rag 공통 진입점)
```

## 부록 C. 코드 맵 (패키지 → 역할)

```
com.igemoney.igemoney_BE
├─ common/embedding/        EmbeddingClient, VoyageEmbeddingClient        [임베딩]
├─ common/vector/           VectorDataSourceConfig, JdbcVectorStoreRepository,
│                           DocumentChunkRecord, RetrievedChunk, SimilarQuizHit  [벡터 DB]
├─ knowledge/               KnowledgeDocumentParser, KnowledgeIngestionService,
│  │                        KnowledgeIngestionRunner, KnowledgeEvaluationRunner  [적재]
│  ├─ chunking/             ChunkingStrategy + 6종 구현, SentenceSplitter,
│  │                        ChunkingConstants, ParsedDocument, Section, Chunk    [청킹]
│  ├─ eval/                 RetrievalEvaluationService, DatasetLoader, Metrics   [실험]
│  └─ controller/           KnowledgeAdminController                             [관리 API]
└─ quiz/service/
   ├─ generate/             QuizGenerationService(인터페이스)
   │                        ├─ MockQuizGenerationService   (mode=mock, 기본)
   │                        └─ RagQuizGenerationService    (mode=rag)
   │                        QuizLlmClient / AnthropicQuizLlmClient               [LLM]
   │                        QuizSimilarityService(인터페이스)
   │                        ├─ SimpleQuizSimilarityService (mock, Jaccard)
   │                        └─ VectorQuizSimilarityService (rag, 코사인)         [유사도]
   └─ sync/                 QuizEmbeddingSyncService, QuizEmbeddingEventListener [동기화]

data/knowledge/*.md         지식 코퍼스 30개 (front-matter: source/topic + ## 헤딩)
data/eval/retrieval-eval.yml  골든 질의셋 60개
docs/sql/vector-schema.sql  Supabase 스키마 (document_chunks, quiz_embeddings)
docs/RAG_SETUP.md           환경 셋업 가이드
docs/RAG_IMPLEMENTATION_PLAN.md  구현 작업지시서 (원본 계획)
```
