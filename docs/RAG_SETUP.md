# RAG 설정 및 실행 가이드

이 문서는 관리자 퀴즈 생성에 RAG 모드를 적용하기 위한 실행 절차를 정리한다. MySQL은 원본 데이터베이스이고, Supabase PostgreSQL/pgvector는 언제든 재구축 가능한 검색 인덱스다.

## 1. Supabase 벡터 DB 준비

1. Supabase 프로젝트를 생성한다.
2. Project Settings > Database에서 PostgreSQL 연결 문자열을 확인한다. JDBC URL은 SSL을 포함해 `jdbc:postgresql://<host>:5432/postgres?sslmode=require` 형식으로 사용한다.
3. Supabase SQL Editor에서 [docs/sql/vector-schema.sql](sql/vector-schema.sql)을 실행한다.
4. `.env.example`을 참고해 `.env`에 벡터 DB와 외부 API 키를 설정한다.

```dotenv
VECTOR_DB_URL=jdbc:postgresql://<host>:5432/postgres?sslmode=require
VECTOR_DB_USERNAME=postgres
VECTOR_DB_PASSWORD=<password>
VECTOR_STORE_ENABLED=true

VOYAGE_API_KEY=<voyage-api-key>
ANTHROPIC_API_KEY=<anthropic-api-key>
QUIZ_GENERATION_MODE=rag
RAG_CHUNKING_STRATEGY=structure
```

기본값은 `VECTOR_STORE_ENABLED=false`, `QUIZ_GENERATION_MODE=mock`이므로 로컬/CI 빌드는 외부 API 없이 통과해야 한다.

## 2. 지식 문서 적재

지식 문서는 `data/knowledge/`에 마크다운 파일로 둔다. 파일명은 `{topic-slug}__{document-name}.md` 형식이며, 공통 문서는 `common__...md`를 사용한다. 각 파일은 front matter에 `source`, `topic`을 포함한다.

```markdown
---
source: 한국은행 경제금융용어 700선
topic: living-economy
---
## 예금과 적금
본문...
```

6개 청킹 전략 전체를 적재하려면 다음처럼 실행한다.

```bash
./gradlew bootRun --args='--ingest-knowledge'
```

일부 전략만 재적재할 때는 쉼표로 지정한다.

```bash
./gradlew bootRun --args='--ingest-knowledge --strategies=structure,hybrid'
```

관리자 API로도 실행할 수 있다.

```bash
curl -X POST http://localhost:8080/api/admin/knowledge/reindex \
  -H "Authorization: Bearer <admin-jwt>" \
  -H "Content-Type: application/json" \
  -d '{"strategies":["fixed","fixed_overlap","sentence_window","semantic","structure","hybrid"]}'
```

## 3. 청킹 전략 평가

평가 질의셋은 `data/eval/retrieval-eval.yml`을 사용한다. 평가를 실행하면 `docs/experiments/chunking-eval-{yyyyMMdd-HHmm}.md` 리포트가 생성되고, Hit@1, Hit@3, Recall@5, MRR@5 기준으로 권장 전략이 기록된다.

```bash
./gradlew bootRun --args='--evaluate-chunking'
```

특정 전략만 비교할 수도 있다.

```bash
./gradlew bootRun --args='--evaluate-chunking --strategies=structure,hybrid'
```

관리자 API:

```bash
curl -X POST http://localhost:8080/api/admin/knowledge/evaluate \
  -H "Authorization: Bearer <admin-jwt>" \
  -H "Content-Type: application/json" \
  -d '{"strategies":["structure","hybrid"]}'
```

리포트의 결론을 보고 `.env`의 `RAG_CHUNKING_STRATEGY` 값을 확정한다.

## 4. RAG 모드로 퀴즈 초안 생성

RAG 모드는 벡터 저장소와 임베딩, Claude API를 사용한다. `.env`에 다음 값을 켠 뒤 애플리케이션을 실행한다.

```dotenv
VECTOR_STORE_ENABLED=true
QUIZ_GENERATION_MODE=rag
RAG_CHUNKING_STRATEGY=structure
```

```bash
./gradlew bootRun
```

관리자 퀴즈 생성 API 예시:

```bash
curl -X POST http://localhost:8080/api/admin/quizzes/generate \
  -H "Authorization: Bearer <admin-jwt>" \
  -H "Content-Type: application/json" \
  -d '{
    "topicId": 1,
    "questionType": "MULTIPLE_CHOICE",
    "difficultyLevel": "EASY",
    "count": 3,
    "additionalPrompt": "예금자보호와 원금 보장 개념을 구분하는 문제"
  }'
```

퀴즈 유사도 검색 인덱스를 전체 재구축하려면 다음 API를 호출한다.

```bash
curl -X POST http://localhost:8080/api/admin/knowledge/reindex-quizzes \
  -H "Authorization: Bearer <admin-jwt>"
```

## 5. 운영 메모

- `.env`는 커밋하지 않는다. 공유 가능한 키 목록만 `.env.example`에 둔다.
- `VECTOR_STORE_ENABLED=false`이면 벡터 DB 관련 빈과 관리자 지식 API는 활성화되지 않는다.
- 지식 청크 적재는 같은 전략의 기존 청크를 삭제한 뒤 다시 넣는 방식이라 반복 실행 가능하다.
- 퀴즈 저장 후 임베딩 동기화 실패는 경고 로그만 남기며 MySQL 저장 결과를 되돌리지 않는다. 필요하면 `/api/admin/knowledge/reindex-quizzes`로 복구한다.
- 실제 퀴즈 품질 비교는 [docs/experiments/quiz-quality-rubric.md](experiments/quiz-quality-rubric.md)를 사용해 상위 전략을 블라인드 평가한다.
