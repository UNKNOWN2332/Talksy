package uz.shukrullaev.com.talksy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@EnableJpaAuditing
@ComponentScan("uz.shukrullaev.com")
@EnableJpaRepositories(
    basePackages = ["uz.shukrullaev.com.talksy"],
    repositoryBaseClass = BaseRepositoryImpl::class,
)
class TalksyApplication

fun main(args: Array<String>) {
    runApplication<TalksyApplication>(*args)
}
