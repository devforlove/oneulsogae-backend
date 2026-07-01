plugins {
	id("meeple.kotlin-conventions")
}

dependencies {
	// 순수 매칭 알고리즘. 공용 enum만 참조하고 프레임워크·인프라에 의존하지 않는다.
	implementation(project(":meeple-common"))
}
