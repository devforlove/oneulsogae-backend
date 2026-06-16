# 코딩 원칙

> CLAUDE.md의 코딩 원칙 요약에 대한 상세 레퍼런스 + 예시.

## 타입 명시 (Explicit Types)

타입을 생략하지 말고 항상 명시적으로 기입한다. 타입 추론(type inference)에 의존하지 않는다.

- 변수/프로퍼티 선언 시 타입을 명시한다.
  ```kotlin
  // ❌ 지양
  val count = 0
  val name = user.getName()

  // ✅ 지향
  val count: Int = 0
  val name: String = user.getName()
  ```
- 함수의 반환 타입을 명시한다. 표현식 본문(expression body) 함수도 반환 타입을 기입한다.
  ```kotlin
  // ❌ 지양
  fun findUser(id: Long) = repository.findById(id)

  // ✅ 지향
  fun findUser(id: Long): User = repository.findById(id)
  ```
- 람다/콜백의 파라미터 타입도 가능한 한 명시한다.

## 도메인 검증 (Validation in Domain)

도메인 상태에 대한 검증은 **서비스에 `if ... throw`를 나열하지 말고, 도메인 모델의 검증 함수로 캡슐화**한다. 서비스는 그 함수를 한 번 호출한다.

- 검증 함수는 `validate<대상>(...)` 형태로 도메인 모델에 두고, 위반 시 도메인별 `ErrorCode` + `BusinessException`을 던진다.
- 검증에 필요한 입력(예: 사용자가 입력한 코드, 현재 시각 `now`)은 파라미터로 받는다. (도메인은 시계/인프라에 의존하지 않는다)
- 참고 선례: `User.validateRegistered()`, `CompanyEmailVerification.validate(code, now)`.

```kotlin
// ❌ 지양: 서비스에 검증 분기를 나열
if (verification.code != code) throw BusinessException(UserErrorCode.VERIFICATION_CODE_MISMATCH)
if (verification.isVerified) throw BusinessException(UserErrorCode.VERIFICATION_ALREADY_VERIFIED)
if (verification.isExpired(now)) throw BusinessException(UserErrorCode.VERIFICATION_EXPIRED)

// ✅ 지향: 도메인 모델에 검증을 위임
verification.validate(code, now)

// CompanyEmailVerification (domain)
fun validate(code: String, now: LocalDateTime) {
    if (this.code != code) throw BusinessException(UserErrorCode.VERIFICATION_CODE_MISMATCH)
    if (isVerified) throw BusinessException(UserErrorCode.VERIFICATION_ALREADY_VERIFIED)
    if (isExpired(now)) throw BusinessException(UserErrorCode.VERIFICATION_EXPIRED)
}
```

## 일급 컬렉션 (First-Class Collection)

도메인 모델/읽기 모델의 **컬렉션은 원시 `List`/`Set`을 그대로 노출하지 말고, 이를 감싼 일급 컬렉션으로 반환**한다. 컬렉션에 대한 동작(개수·필터·합계 등)을 한곳에 응집시키고, 시그니처를 안정적인 타입으로 고정하기 위함이다.

- 포트(out/in)·dao·도메인 함수가 여러 건을 반환할 때 `List<X>`가 아니라 `Xs`(복수형) 형태의 래퍼를 반환한다.
- 래퍼는 원시 리스트를 `values` 프로퍼티로 보관하고, 컬렉션 관련 동작을 메서드로 제공한다. (서비스/어댑터에 흩어진 `map`/`filter`/`sum`을 한곳에 모은다)
- 참고 선례: `CoinItems`(코인 상품 목록 일급 컬렉션, `CoinItemQueryDao.findAll(): CoinItems`).

```kotlin
// ❌ 지양: 원시 List를 그대로 반환
interface CoinItemQueryDao {
    fun findAll(): List<CoinItem>
}

// ✅ 지향: 일급 컬렉션으로 감싸 반환
interface CoinItemQueryDao {
    fun findAll(): CoinItems
}

// CoinItems (query/dto) — 컬렉션 동작을 응집
data class CoinItems(
    val values: List<CoinItem>,
) {
    val size: Int get() = values.size
    fun isEmpty(): Boolean = values.isEmpty()
}
```
