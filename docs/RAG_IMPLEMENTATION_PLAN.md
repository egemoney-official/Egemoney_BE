# RAG 기반 퀴즈 생성 구현 계획서 (AI Agent 실행용)

> 이 문서는 AI 코딩 에이전트가 그대로 실행할 수 있도록 작성된 작업 지시서다.
> 각 Phase를 순서대로 진행하고, Phase마다 "완료 기준"을 만족하는지 확인한 후 다음 Phase로 넘어간다.

---

## 1. 프로젝트 컨텍스트 (먼저 읽을 것)

- **서비스**: 금융문맹 해소를 위한 금융 퀴즈 서비스 (Spring Boot 3.5.5, Java 21, Gradle)
- **기본 DB**: MySQL (JPA/Hibernate, `spring.datasource`로 설정됨, `application-dev.yml` 참고)
- **목표**:
  1. 관리자 퀴즈 생성 기능에 RAG를 적용한다.
  2. **6가지 청킹 전략을 구현하고, 검색 품질을 정량 비교하는 실험 체계를 구축한다.**
- 기술 스택:
  - 벡터 저장소: **Supabase (PostgreSQL + pgvector)** — MySQL과 별개의 외부 DB
  - 임베딩: **Voyage AI** (`voyage-3.5`, 1024차원, REST API)
  - LLM: **Anthropic Claude** (공식 Java SDK `com.anthropic:anthropic-java:2.34.0`, 모델 `claude-opus-4-8`)

### 1.1 반드시 파악할 기존 코드 (수정 전 필독)

| 파일 | 역할 |
|---|---|
| `src/main/java/com/igemoney/igemoney_BE/quiz/service/generate/QuizGenerationService.java` | 퀴즈 초안 생성 인터페이스 (`generateDrafts(QuizGenerateRequest)`) |
| `src/main/java/com/igemoney/igemoney_BE/quiz/service/generate/MockQuizGenerationService.java` | 현재 `@Primary`인 목(mock) 구현체 |
| `src/main/java/com/igemoney/igemoney_BE/quiz/service/generate/QuizSimilarityService.java` | 유사도 분석 인터페이스 (`analyzeCandidate(s)`) |
| `src/main/java/com/igemoney/igemoney_BE/quiz/service/generate/SimpleQuizSimilarityService.java` | Jaccard 기반 단순 유사도 구현체 |
| `src/main/java/com/igemoney/igemoney_BE/quiz/service/generate/DefaultAdminQuizGenerationService.java` | 오케스트레이션: 생성 → 유사도 분석 → 응답 |
| `src/main/java/com/igemoney/igemoney_BE/quiz/dto/generate/GeneratedQuizDraft.java` | 초안 DTO (record) |
| `src/main/java/com/igemoney/igemoney_BE/quiz/controller/AdminQuizController.java` | 관리자 API 진입점 |
| `src/main/java/com/igemoney/igemoney_BE/quiz/service/create/QuizCreateService.java` | 퀴즈 저장 서비스 (임베딩 동기화 훅 추가 지점) |
| `src/test/**` | 기존 계약 테스트 — **깨뜨리면 안 됨** |

### 1.2 아키텍처 원칙 (위반 금지)

1. **MySQL이 원본(source of truth)**. Supabase는 재구축 가능한 검색 인덱스일 뿐이다. 두 DB 간 트랜잭션·FK는 없다. Supabase 쪽에는 MySQL의 `quiz_id`, `topic_id`를 일반 컬럼으로 저장해 논리적으로만 연결한다.
2. **JPA는 MySQL 전용으로 유지**한다. Supabase 접근은 별도 `DataSource` + `JdbcTemplate`으로만 한다. Supabase용 JPA 엔티티를 만들지 않는다.
3. **기존 API 계약(컨트롤러, DTO, 응답 JSON 구조)을 변경하지 않는다.** 구현체 교체만 한다.
4. Supabase/임베딩/LLM 호출 실패가 **퀴즈 저장(MySQL 커밋)을 실패시키면 안 된다.** 벡터 동기화 실패는 로그만 남기고 넘어간다 (리인덱스로 복구 가능해야 함).
5. **Spring AI 프레임워크를 도입하지 않는다.** 공식 Anthropic Java SDK + 직접 만든 얇은 클라이언트를 사용한다.
6. **시크릿을 커밋하지 않는다.** 모든 키는 환경변수/`.env`로 주입한다 (`spring-dotenv` 이미 적용됨). `.env.example`만 갱신한다.
7. 외부 API를 호출하는 코드는 전부 인터페이스 뒤에 두어, 테스트에서 스텁으로 대체 가능해야 한다.
8. **청킹 전략은 플러그인 구조**로 만든다. 전략 추가/제거가 다른 코드에 영향을 주면 안 되고, 모든 전략의 산출물은 동일한 스키마(`document_chunks`)에 `chunking_strategy` 값으로 구분되어 공존한다.

---

## 2. 사전 준비 (사람이 수행 — 에이전트는 이 값이 주어졌다고 가정)

에이전트 작업 시작 전 아래 환경변수가 `.env`에 준비된다. 에이전트는 `.env.example`에 키 이름만 추가한다.

```
# Supabase (Settings > Database > Connection string, ssl 필수)
VECTOR_DB_URL=jdbc:postgresql://<host>:5432/postgres?sslmode=require
VECTOR_DB_USERNAME=postgres
VECTOR_DB_PASSWORD=<password>

# Voyage AI (https://dashboard.voyageai.com)
VOYAGE_API_KEY=<key>

# Anthropic (https://console.anthropic.com)
ANTHROPIC_API_KEY=<key>
```

Supabase SQL Editor에서 1회 실행할 스키마 (에이전트는 이 스키마가 존재한다고 가정하되, 동일 SQL을 `docs/sql/vector-schema.sql`로 저장해 둘 것):

```sql
create extension if not exists vector;

-- RAG 지식 청크. 여러 청킹 전략의 산출물이 chunking_strategy 값으로 구분되어 공존한다.
create table if not exists document_chunks (
  id bigserial primary key,
  chunking_strategy text not null,       -- fixed | fixed_overlap | sentence_window | semantic | structure | hybrid
  topic_id bigint,
  source_file text not null,             -- 원본 파일명 (평가 시 정답 라벨 매칭에 사용)
  heading text,                          -- 청크가 속한 마크다운 헤딩 (구조 정보가 없으면 null)
  content text not null,
  source text,                           -- 출처 표기 (예: 한국은행 경제금융용어 700선)
  char_length int not null,
  embedding vector(1024) not null,
  created_at timestamptz not null default now()
);
create index if not exists idx_document_chunks_embedding
  on document_chunks using hnsw (embedding vector_cosine_ops);
create index if not exists idx_document_chunks_strategy_topic
  on document_chunks (chunking_strategy, topic_id);

-- 퀴즈 유사도 검사용 (청킹 실험과 무관, 단일 저장)
create table if not exists quiz_embeddings (
  quiz_id bigint primary key,
  topic_id bigint not null,
  question_title text not null,
  embedding vector(1024) not null,
  updated_at timestamptz not null default now()
);
create index if not exists idx_quiz_embeddings_embedding
  on quiz_embeddings using hnsw (embedding vector_cosine_ops);
create index if not exists idx_quiz_embeddings_topic on quiz_embeddings (topic_id);
```

---

## 3. Phase 1 — 의존성 및 벡터 DB 연결 계층

### 작업

1. `build.gradle`에 추가:
   ```groovy
   runtimeOnly 'org.postgresql:postgresql'
   implementation 'com.anthropic:anthropic-java:2.34.0'
   ```
2. `application.yml`(공통) 또는 `application-dev.yml`에 커스텀 프리픽스로 벡터 DB 설정 추가 (스프링 기본 `spring.datasource`와 충돌 금지):
   ```yaml
   vector:
     datasource:
       url: ${VECTOR_DB_URL:}
       username: ${VECTOR_DB_USERNAME:}
       password: ${VECTOR_DB_PASSWORD:}
     enabled: ${VECTOR_STORE_ENABLED:false}
   quiz:
     generation:
       mode: ${QUIZ_GENERATION_MODE:mock}   # mock | rag
   rag:
     retrieval:
       strategy: ${RAG_CHUNKING_STRATEGY:structure}  # 검색 시 사용할 청킹 전략 (실험 후 최종 선정값으로 변경)
       top-k: 6
   ```
3. 새 패키지 `com.igemoney.igemoney_BE.common.vector` 생성:
   - `VectorDataSourceConfig`: `@ConditionalOnProperty(name = "vector.enabled", havingValue = "true")`.
     `HikariDataSource`를 `vector.datasource.*`로 수동 구성(`@ConfigurationProperties` 사용), 빈 이름 `vectorDataSource`.
     기존 MySQL `DataSource`가 `@Primary`가 되도록 보장 (자동구성 DataSource가 primary로 유지되는지 확인하고, 필요 시 명시).
     `@Bean @Qualifier("vectorJdbcTemplate") JdbcTemplate` 제공.
   - HikariCP 설정: `maximumPoolSize=3`, `connectionTimeout=3000` (Supabase 무료 티어 커넥션 제한 고려).
4. JPA `ddl-auto`/`data.sql` 초기화가 벡터 DataSource에 영향을 주지 않는지 확인 (JPA는 primary DataSource만 사용).

### 완료 기준
- `VECTOR_STORE_ENABLED=false`(기본값)일 때 애플리케이션이 벡터 DB 없이 기존과 동일하게 기동한다.
- `./gradlew build` 성공, 기존 테스트 전부 통과.

---

## 4. Phase 2 — 임베딩 클라이언트

### 작업

1. `com.igemoney.igemoney_BE.common.embedding` 패키지:
   - `EmbeddingClient` 인터페이스:
     ```java
     public interface EmbeddingClient {
         float[] embedDocument(String text);
         float[] embedQuery(String text);
         List<float[]> embedAllDocuments(List<String> texts); // 배치 (최대 128개/호출, 초과 시 내부에서 분할)
         int dimension(); // 1024
     }
     ```
   - `VoyageEmbeddingClient implements EmbeddingClient`:
     - Spring `RestClient`로 `POST https://api.voyageai.com/v1/embeddings` 호출
     - 요청: `{"model": "voyage-3.5", "input": [...], "input_type": "document"}` (질의는 `"query"`)
     - 헤더: `Authorization: Bearer ${VOYAGE_API_KEY}`
     - 응답의 `data[i].embedding` → `float[]`
     - 타임아웃 10초, 429/5xx 시 1회 재시도(간단한 backoff)
     - `@ConditionalOnProperty(name = "vector.enabled", havingValue = "true")`
2. 설정 키: `embedding.voyage.api-key: ${VOYAGE_API_KEY:}`, `embedding.voyage.model: voyage-3.5`

> **주의**: 의미적 청킹(Phase 4)이 문장 단위 임베딩을 대량 호출하므로, `embedAllDocuments`의 배치 처리가 반드시 정상 동작해야 한다.

### 완료 기준
- `EmbeddingClient`를 스텁으로 대체한 단위 테스트 존재 (요청 JSON 직렬화/응답 파싱 검증).
- 실제 API 키 없이도 빌드/테스트 통과.

---

## 5. Phase 3 — 벡터 저장소 리포지토리

### 작업

`com.igemoney.igemoney_BE.common.vector` 패키지에 `VectorStoreRepository` 작성 (`@Qualifier("vectorJdbcTemplate")` 주입, `vector.enabled=true` 조건부):

```java
public interface VectorStoreRepository {
    // document_chunks — 모든 조회/삭제는 chunking_strategy로 스코프된다
    void insertChunks(List<DocumentChunkRecord> chunks);                     // batch insert
    List<RetrievedChunk> searchChunks(String strategy, float[] queryEmbedding, Long topicId, int topK);
    int deleteChunks(String strategy, Long topicId);                        // topicId null이면 전략 전체 삭제
    Map<String, ChunkStats> chunkStatsByStrategy();                         // 전략별 개수/평균 길이 (실험 리포트용)

    // quiz_embeddings
    void upsertQuizEmbedding(long quizId, long topicId, String questionTitle, float[] embedding);
    void deleteQuizEmbedding(long quizId);
    List<SimilarQuizHit> searchSimilarQuizzes(float[] embedding, long topicId, int topK);
}
```

- `DocumentChunkRecord`는 스키마 컬럼(`chunkingStrategy`, `topicId`, `sourceFile`, `heading`, `content`, `source`, `charLength`, `embedding`)을 담는 record.
- `RetrievedChunk`는 `content`, `sourceFile`, `heading`, `similarity`를 포함해야 한다 (평가 지표 계산에 필요).
- pgvector 벡터 리터럴은 `"[0.1,0.2,...]"` 문자열로 만들어 `?::vector`로 캐스팅한다.
- 유사도 검색 SQL 예시 (코사인 거리 `<=>`, 유사도 = `1 - 거리`):
  ```sql
  select content, source_file, heading, 1 - (embedding <=> ?::vector) as similarity
  from document_chunks
  where chunking_strategy = ?
    and (? is null or topic_id = ? or topic_id is null)
  order by embedding <=> ?::vector
  limit ?
  ```

### 완료 기준
- `JdbcTemplate`을 목으로 한 단위 테스트가 벡터 리터럴 변환, strategy 스코핑, 파라미터 바인딩을 검증한다.

---

## 6. Phase 4 — 청킹 전략 모듈 (실험 핵심)

### 설계

`com.igemoney.igemoney_BE.knowledge.chunking` 패키지. 모든 전략은 공통 인터페이스를 구현한다:

```java
public interface ChunkingStrategy {
    String name();  // DB의 chunking_strategy 값과 동일
    List<Chunk> chunk(ParsedDocument document);
}

// ParsedDocument: sourceFile, source, topicSlug, List<Section> (heading + 본문. 헤딩 없으면 단일 섹션)
// Chunk: content, heading(가능한 경우), charLength
```

전략 레지스트리: `Map<String, ChunkingStrategy>`를 구성하는 `ChunkingStrategyRegistry` 빈. 이름으로 조회, 전체 목록 반환 지원.

### 구현할 6가지 전략

공통 상수: `TARGET_SIZE=500`(자), `MAX_SIZE=1000`, `MIN_SIZE=100`, `OVERLAP=100`.
문장 분리는 정규식 기반 유틸 `SentenceSplitter`로 공통화한다 (한국어 종결부호 `. ! ? 다.` + 개행 기준, 완벽할 필요 없음 — 전 전략이 동일 유틸을 쓰는 것이 공정 비교의 핵심).

| # | `name()` | 전략 | 구현 규칙 |
|---|---|---|---|
| 1 | `fixed` | 고정 길이 청킹 | 문서 전체 텍스트를 500자 단위로 기계적으로 절단. 문장/구조 경계 무시. **베이스라인** |
| 2 | `fixed_overlap` | 고정 길이 + 오버랩 | 500자 윈도우를 400자 간격으로 슬라이딩 (뒤 100자가 다음 청크와 중복) |
| 3 | `sentence_window` | 문장 단위 슬라이딩 윈도우 (오버랩 청킹의 문장 경계 변형) | 문장 단위로 분리 후, 윈도우에 문장을 채워 500자 근접 시 청크 확정. 다음 청크는 직전 청크의 **마지막 1~2문장을 포함**하고 시작 |
| 4 | `semantic` | 의미적 청킹 | 문장 분리 → 각 문장 임베딩(`embedAllDocuments` 배치) → 인접 문장 간 코사인 유사도 계산 → 유사도가 `평균 − 1×표준편차` 아래로 떨어지는 지점을 분할 후보로 → MIN/MAX 크기 제약 적용해 청크 확정 |
| 5 | `structure` | 구조 기반 청킹 | 마크다운 헤딩(`##`) 단위로 분할. 섹션이 MAX_SIZE 초과 시 문단(빈 줄) 단위로 재분할. 각 청크에 heading 보존 |
| 6 | `hybrid` | 하이브리드 (구조+의미) | `structure`로 1차 분할 → MAX_SIZE를 초과하는 섹션에 한해 섹션 내부에서 `semantic` 로직으로 2차 분할. heading은 상위 섹션 것을 상속 |

구현 참고:
- `semantic`과 `hybrid`는 `EmbeddingClient`에 의존한다 — 생성자 주입으로 받고, 단위 테스트에서는 결정적(deterministic) 스텁 임베딩(예: 문장 해시 기반 고정 벡터)을 사용한다.
- 유사도 계산용 코사인 함수는 순수 자바 유틸 `VectorMath.cosineSimilarity(float[], float[])`로 구현 (외부 의존 금지).
- 모든 전략은 순수 함수처럼 동작해야 한다: 같은 입력 → 같은 출력 (semantic 계열은 임베딩 결과가 같다는 전제 하에).

### 완료 기준
- 6개 전략 각각에 대한 단위 테스트:
  - `fixed`: 정확히 500자 단위 절단, 마지막 잔여분 처리
  - `fixed_overlap`: 인접 청크 간 100자 중복 검증
  - `sentence_window`: 청크 경계가 항상 문장 경계이고, 인접 청크가 문장을 공유함
  - `semantic`: 유사도가 급락하는 합성 데이터에서 의도한 지점에 분할이 생기는지 (스텁 임베딩 사용)
  - `structure`: 헤딩별 분할 + 초과 섹션의 문단 재분할 + heading 보존
  - `hybrid`: 짧은 섹션은 구조 분할 그대로, 긴 섹션만 2차 분할되는지
- MIN_SIZE 미만 청크가 생성되지 않음(인접 청크와 병합)을 공통 테스트로 검증.

---

## 7. Phase 5 — 지식 문서 적재 (Ingestion, 전략 인식)

### 지식 문서 소스 (데이터 준비 가이드)

`data/knowledge/` 디렉토리(리포지토리 루트)에 마크다운 파일로 지식 문서를 둔다. 파일 명명 규칙: `{topic-slug}__{문서명}.md`, 공통 문서는 `common__{문서명}.md`.

권장 원천 자료 (사람이 수집·정제하며, 에이전트는 샘플 파일만 생성):
- 한국은행 『경제금융용어 700선』 (PDF 공개, 출처표시 필요) — 용어 정의 중심
- 금융감독원 e-금융교육센터 『대학생을 위한 실용금융』 e-book — 주제별 개념 설명
- 금융감독원 파인(FINE) 『금융꿀팁 200선』 — 실생활 금융 상식
- 한국예탁결제원 금융용어조회서비스 (공공데이터포털 Open API) — 용어 보강

각 파일 형식:
```markdown
---
source: 한국은행 경제금융용어 700선
topic: savings        # MySQL QuizTopic과 매핑할 슬러그, 공통이면 common
---
## 단리와 복리
단리는 원금에 대해서만 이자를 계산하는 방식이고, ...
```

### 작업

1. `com.igemoney.igemoney_BE.knowledge` 패키지:
   - `KnowledgeDocumentParser`: front-matter(source, topic) + 본문을 `ParsedDocument`로 파싱 (헤딩 구조 포함)
   - `KnowledgeIngestionService`:
     ```java
     void ingest(List<String> strategyNames);  // 지정 전략들로 전체 문서 적재 (기존 해당 전략 청크 삭제 후 재적재 — idempotent)
     ```
     플로우: 파일 로드 → 파싱 → **전략별로** 청킹 → topic 슬러그를 MySQL `QuizTopic`과 매핑 → `embedAllDocuments` 배치 임베딩 → `insertChunks`
   - 전략별 적재 결과 요약을 로그와 반환값으로 제공: 전략명, 청크 수, 평균/최대 길이, 임베딩 호출 수
2. 실행 방법 (둘 다 제공):
   - 관리자 API: `POST /api/admin/knowledge/reindex` — body: `{"strategies": ["fixed","semantic"]}` (생략 시 6개 전체)
   - CLI: `./gradlew bootRun --args='--ingest-knowledge --strategies=structure,hybrid'` 로 동작하는 `ApplicationRunner` (인자 있을 때만 실행 후 종료, `--strategies` 생략 시 전체)
3. 샘플 지식 문서 4~5개를 `data/knowledge/`에 생성 (예: 단리/복리, 예금자보호제도, 신용점수, 분산투자, 주식과 채권 — 공개된 일반 상식 수준으로 에이전트가 직접 작성, 파일당 1,500자 이상으로 청킹 실험이 의미 있게)

> **비용 참고**: 6개 전략 전체 적재 시 동일 코퍼스가 6번 임베딩되고, semantic 계열은 문장 단위 임베딩이 추가된다. Voyage 무료 크레딧 내에서 충분하지만, 코퍼스가 커지면 `--strategies`로 선별 적재한다.

### 완료 기준
- 파서 단위 테스트 통과 (front-matter, 헤딩 구조 파싱).
- 스텁 기반 `KnowledgeIngestionService` 테스트: 전략별 삭제→재적재 순서, 배치 임베딩 호출, 요약 통계 검증.

---

## 8. Phase 6 — 청킹 전략 평가 실험

> 이 Phase의 목적: "어떤 청킹이 우리 데이터에서 검색 품질이 가장 좋은가"를 수치로 답하고, 그 결과로 `rag.retrieval.strategy` 기본값을 확정한다.

### 8.1 평가 데이터셋

`data/eval/retrieval-eval.yml` 생성. 골든 질의셋 — 각 항목은 "이 질문의 근거는 이 문서의 이 섹션"이라는 라벨:

```yaml
- query: "복리와 단리의 차이는 무엇인가?"
  relevantSourceFile: "savings__금리기초.md"
  relevantHeading: "단리와 복리"          # heading이 없는 전략은 sourceFile 일치만으로 판정
  topic: savings
- query: "은행이 파산하면 내 예금은 얼마까지 보호받나?"
  relevantSourceFile: "common__예금자보호.md"
  relevantHeading: "예금자보호 한도"
  topic: common
# ... 최소 20개 (에이전트가 샘플 지식 문서 내용 기반으로 20개 작성, 문서당 4개 이상)
```

정답 판정 규칙: 검색된 청크의 `source_file`이 라벨과 일치하면 relevant. `heading` 라벨이 있고 청크에 heading이 존재하면 heading까지 일치해야 relevant (fixed 계열처럼 heading이 null인 전략은 sourceFile 일치만 적용 — 리포트에 판정 기준 차이를 명시).

### 8.2 평가 러너

`com.igemoney.igemoney_BE.knowledge.eval` 패키지:

- `RetrievalEvaluationService`:
  - 입력: 평가할 전략 목록(기본: DB에 청크가 존재하는 모든 전략), 골든 질의셋
  - 각 질의를 `embedQuery` → 전략별 `searchChunks(topK=5)` → 지표 계산
  - **전략별 지표**: `Hit@1`, `Hit@3`, `Recall@5`, `MRR@5`
  - **전략별 부가 통계**: 청크 수, 평균 청크 길이, 검색된 top-1 유사도 평균
  - 질의 임베딩은 질의당 1회만 수행해 전 전략에 재사용 (공정 비교 + 비용 절약)
- 결과 출력: `docs/experiments/chunking-eval-{yyyyMMdd-HHmm}.md`에 마크다운 리포트 생성:
  ```markdown
  # 청킹 전략 평가 리포트 (2026-07-05 14:00, 질의 20개, top-k=5)
  | 전략 | 청크수 | 평균길이 | Hit@1 | Hit@3 | Recall@5 | MRR@5 |
  |---|---|---|---|---|---|---|
  | fixed | 142 | 498 | 0.55 | 0.75 | 0.80 | 0.63 |
  | ...
  ## 질의별 상세 (전략별 top-3 청크와 정답 여부)
  ...
  ## 결론
  (최고 지표 전략과 권장 rag.retrieval.strategy 값을 자동 기재)
  ```
- 실행 방법:
  - CLI: `./gradlew bootRun --args='--evaluate-chunking'`
  - 관리자 API: `POST /api/admin/knowledge/evaluate` → 리포트 내용을 응답으로도 반환

### 8.3 실험 절차 (리포트에 함께 문서화)

1. `--ingest-knowledge` (6개 전략 전체 적재)
2. `--evaluate-chunking` 실행 → 리포트 확인
3. 최고 전략을 `RAG_CHUNKING_STRATEGY` 값으로 채택
4. (선택·수동) 상위 2개 전략으로 실제 퀴즈를 각 10개씩 생성해 팀원이 품질 루브릭(사실 정확성/난이도 적합성/해설 품질, 각 1~5점)으로 블라인드 평가 — 이 단계는 사람이 수행하며, 에이전트는 루브릭 템플릿(`docs/experiments/quiz-quality-rubric.md`)만 만들어 둔다.

### 완료 기준
- 스텁 임베딩 + 인메모리 가짜 `VectorStoreRepository`로 `RetrievalEvaluationService` 단위 테스트: 지표 계산(Hit/Recall/MRR)이 수학적으로 정확한지 알려진 케이스로 검증.
- 골든 질의셋 20개 이상 존재, 리포트 생성 로직 동작.

---

## 9. Phase 7 — RAG 퀴즈 생성 서비스 (Claude 연동)

### 작업

1. `com.igemoney.igemoney_BE.quiz.service.generate.RagQuizGenerationService implements QuizGenerationService` 작성.
2. 활성화 전략 변경:
   - `MockQuizGenerationService`의 `@Primary` 제거 → `@ConditionalOnProperty(name = "quiz.generation.mode", havingValue = "mock", matchIfMissing = true)`
   - `RagQuizGenerationService` → `@ConditionalOnProperty(name = "quiz.generation.mode", havingValue = "rag")`
   - 기존 테스트가 `MockQuizGenerationService`를 직접 참조한다면 그대로 동작해야 한다.
3. 생성 플로우:
   1. 토픽 조회 (기존 `TopicRepository`, 없으면 `TopicNotFoundException` — Mock 구현과 동일한 검증 유지)
   2. 질의 텍스트 = `"{토픽명} {additionalPrompt}"` → `embedQuery` → `searchChunks(rag.retrieval.strategy 설정값, topK=rag.retrieval.top-k)`
   3. 프롬프트 구성 후 Claude 호출 → 초안 `count`개 생성
   4. `GeneratedQuizDraft` 리스트로 매핑해 반환 (기존 record 구조 그대로)
4. **Anthropic Java SDK 사용법 (이대로 사용, 임의 변경 금지):**
   ```java
   import com.anthropic.client.AnthropicClient;
   import com.anthropic.client.okhttp.AnthropicOkHttpClient;
   import com.anthropic.models.messages.MessageCreateParams;
   import com.anthropic.models.messages.StructuredMessageCreateParams;
   import com.anthropic.models.messages.ThinkingConfigAdaptive;

   AnthropicClient client = AnthropicOkHttpClient.fromEnv(); // ANTHROPIC_API_KEY 자동 인식

   // 구조화 출력: 응답 스키마를 record로 정의하면 SDK가 JSON 스키마 생성·파싱까지 처리
   record QuizChoiceDraft(int sequence, String content, boolean answer) {}
   record QuizDraftItem(String questionTitle, String questionType, String difficultyLevel,
                        String explanation, List<QuizChoiceDraft> choices,
                        List<String> subjectiveAnswers) {}
   record QuizDraftBatch(List<QuizDraftItem> quizzes) {}

   StructuredMessageCreateParams<QuizDraftBatch> params = MessageCreateParams.builder()
       .model("claude-opus-4-8")
       .maxTokens(8192L)
       .thinking(ThinkingConfigAdaptive.builder().build())
       .outputConfig(QuizDraftBatch.class)
       .addUserMessage(prompt)
       .build();

   QuizDraftBatch batch = client.messages().create(params).content().stream()
       .flatMap(cb -> cb.text().stream())
       .findFirst().orElseThrow().text();
   ```
   - `AnthropicClient`는 싱글톤 빈으로 등록하고, 직접 호출부는 `QuizLlmClient` 인터페이스로 감싸 테스트에서 스텁 가능하게 한다.
   - `temperature`/`top_p`/`top_k`는 이 모델에서 지원하지 않으므로 **절대 설정하지 않는다** (400 에러).
5. 프롬프트 요구사항 (시스템 프롬프트 + 유저 메시지):
   - 역할: "금융문맹 해소를 위한 한국어 금융 퀴즈 출제자"
   - **제공된 컨텍스트(검색된 청크)에 근거해서만 출제**하고, 컨텍스트에 없는 내용은 지어내지 말 것
   - 요청된 유형(OX / MULTIPLE_CHOICE / SUBJECTIVE)과 난이도 규칙 명시:
     - OX: 보기 2개(O/X), 정답 1개
     - MULTIPLE_CHOICE: 보기 4개, 정답 1개
     - SUBJECTIVE: 보기 없음, 정답 문자열 1개 이상(띄어쓰기 변형 포함)
   - 해설(explanation)은 정답 근거를 컨텍스트 기반으로 2~3문장
   - `count`개를 서로 겹치지 않는 개념으로 생성
   - 컨텍스트가 비어 있으면(검색 결과 0건) 예외를 던지지 말고, 토픽 일반 지식으로 생성하되 "no-context" 경고 로그를 남긴다.
6. LLM 응답 → `GeneratedQuizDraft` 매핑 시 기존 검증 재사용: 유형 파싱, count 상한(5개) 등은 Mock 구현의 규칙과 동일하게 적용.

### 완료 기준
- `QuizLlmClient` 스텁을 사용한 `RagQuizGenerationService` 단위 테스트: 검색 결과가 프롬프트에 포함되는지, 설정된 전략명으로 `searchChunks`가 호출되는지, 응답 매핑·count/유형 검증 동작.
- `quiz.generation.mode` 미설정 시 mock이 선택되어 기존 통합 테스트 전부 통과.

---

## 10. Phase 8 — 벡터 유사도 서비스 및 임베딩 동기화

### 작업

1. `VectorQuizSimilarityService implements QuizSimilarityService`:
   - `@ConditionalOnProperty(name = "quiz.generation.mode", havingValue = "rag")`, `SimpleQuizSimilarityService`는 mock 모드 기본 유지 (`matchIfMissing = true`)
   - 초안 `questionTitle` 임베딩 → `searchSimilarQuizzes(topK=3)` → `SimilarQuizResponse` 매핑
   - 유사도 임계값 상수: `SIMILARITY_THRESHOLD = 0.80`, `EXACT_DUPLICATE_THRESHOLD = 0.95` (기존 `GeneratedQuizCandidateResponse` 계약의 `exactDuplicate`, `maxSimilarityScore` 채우기)
   - `similarityReason` 문구는 기존 `SimpleQuizSimilarityService`의 한국어 문구 스타일 유지
   - `SimilarQuizResponse.from(quiz, ...)`이 Quiz 엔티티를 요구하면 `QuizRepository.findAllById`로 배치 조회
2. 퀴즈 저장/삭제 시 동기화:
   - `QuizEmbeddingSyncService`: `upsert(quiz)`, `delete(quizId)` 제공
   - `QuizCreateService`(및 삭제 경로가 있다면 해당 서비스)에서 **트랜잭션 커밋 후** 호출: `@TransactionalEventListener(phase = AFTER_COMMIT)` 방식 사용
   - 실패 시 WARN 로그만 남기고 예외 전파 금지
3. 전체 리인덱스: `POST /api/admin/knowledge/reindex-quizzes` — MySQL의 모든 퀴즈를 배치로 임베딩해 `quiz_embeddings` 재구축

### 완료 기준
- 스텁 기반 단위 테스트: 임계값 판정, exactDuplicate 판정, 동기화 실패 시 저장 로직이 영향받지 않음을 검증.
- 기존 유사도 관련 테스트(mock 모드) 전부 통과.

---

## 11. Phase 9 — 마무리 검증

1. `./gradlew build` 전체 통과 (rag/실험 관련 테스트는 전부 스텁 기반이어야 하며, CI에서 외부 API 호출이 없어야 한다).
2. `.env.example`에 신규 키 추가: `VECTOR_DB_URL`, `VECTOR_DB_USERNAME`, `VECTOR_DB_PASSWORD`, `VECTOR_STORE_ENABLED`, `VOYAGE_API_KEY`, `ANTHROPIC_API_KEY`, `QUIZ_GENERATION_MODE`, `RAG_CHUNKING_STRATEGY`.
3. `docs/sql/vector-schema.sql`, `docs/experiments/quiz-quality-rubric.md` 존재 확인.
4. `docs/RAG_SETUP.md`에 실행 방법 문서화:
   - Supabase 프로젝트 생성 → 스키마 실행 → `.env` 설정
   - 지식 문서 넣기(`data/knowledge/`) → `--ingest-knowledge`로 6개 전략 적재
   - `--evaluate-chunking`으로 평가 리포트 생성 → `RAG_CHUNKING_STRATEGY` 확정
   - `QUIZ_GENERATION_MODE=rag VECTOR_STORE_ENABLED=true`로 기동 → 관리자 퀴즈 생성 API 호출 예시(curl)
5. 커밋은 Phase 단위로 분리하고, 커밋 메시지는 기존 컨벤션(`feat:`, `refactor:`, `test:` + 한국어 요약)을 따른다.

---

## 12. 하지 말아야 할 것 (요약)

- 기존 컨트롤러/DTO/응답 JSON 구조 변경 금지
- Supabase용 JPA 엔티티 생성 금지 (JdbcTemplate만)
- Spring AI, LangChain4j 등 프레임워크 추가 금지
- `temperature` 등 샘플링 파라미터를 Claude 요청에 추가 금지 (`claude-opus-4-8`에서 400)
- API 키·접속 정보 하드코딩/커밋 금지
- 벡터 저장 실패를 퀴즈 저장 실패로 전파 금지
- 임베딩 모델/차원 변경 금지 (voyage-3.5 / 1024 고정 — 변경 시 전체 리인덱스 필요)
- 청킹 전략 간 비교 조건을 다르게 만들기 금지: 동일한 `SentenceSplitter`, 동일한 크기 상수(TARGET/MAX/MIN/OVERLAP), 동일한 임베딩 모델, 동일한 top-k를 사용해야 실험이 유효하다
- 평가 지표 계산 로직에 전략별 특례 추가 금지 (heading 유무에 따른 판정 기준 차이는 8.1에 정의된 규칙만 허용)
