package com.org.oneulsogae

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling // 배치 실행 로직은 oneulsogae-scheduler 모듈에 있고, api 프로세스가 크론 트리거로 구동한다.
class OneulsogaeApiApplication

fun main(args: Array<String>) {
	runApplication<OneulsogaeApiApplication>(*args)
}
