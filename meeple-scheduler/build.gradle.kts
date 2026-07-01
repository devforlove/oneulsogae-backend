plugins {
	id("meeple.kotlin-conventions")
}

dependencies {
	// scheduler는 core에 의존하지 않는다. 배치에 필요한 포트/도메인을 자체 보유하고, 공용 enum만 common에서 가져온다.
	implementation(project(":meeple-common"))
	// 매칭 스코어링·선택 알고리즘(순수). 실시간 추가 소개(core)와 공유한다.
	implementation(project(":meeple-matching"))

	// 배치 로직이 @Service·slf4j만 사용하므로 최소 의존만 둔다. (실행 가능한 부트 앱이 아닌 라이브러리 모듈)
	implementation("org.springframework:spring-context")
	implementation("org.slf4j:slf4j-api")
}
