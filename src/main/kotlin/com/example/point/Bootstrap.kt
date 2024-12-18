package com.example.point

import com.example.point.adapters.GambleGameFetcher
import com.example.point.adapters.PointCache
import com.example.point.adapters.ProductRepository
import com.example.point.infrastructure.adapters.ConstantGambleGameFetcher
import com.example.point.infrastructure.adapters.ConstantProductRepository
import com.example.point.infrastructure.adapters.ExposedPointRepository
import com.example.point.infrastructure.adapters.RedisPointCache
import com.example.point.infrastructure.getIpAddressByHostname
import com.example.point.infrastructure.uow.RedisExposedUow
import com.example.point.service.MessageBus
import com.example.point.service.UnitOfWork
import io.github.cdimascio.dotenv.dotenv

fun bootstrapBus(
    uow : UnitOfWork? = null,
    gameFetcher: GambleGameFetcher? = null,
    productRepository: ProductRepository? = null
): MessageBus {
    val envs = dotenv{
        filename = ".env"
    }

    // private val redisHost = System.getenv("CACHE_REDIS_HOST")

    // Somehow lettuce cannot connect to the Redis container with the container name as the host name
    // So get the ip address of the host name and use it instead.
    val redisHost = getIpAddressByHostname(envs["CACHE_REDIS_HOST"])
    val redisPort = envs["REDIS_PORT"].toInt()

    return MessageBus(
        uow = uow ?: RedisExposedUow(
            lockRedisHost = redisHost,
            lockRedisPort = redisPort,
            repository = ExposedPointRepository(),
            pointCache = RedisPointCache(redisHost, redisPort),
        ),
        gameFetcher = gameFetcher ?: ConstantGambleGameFetcher(),
        productRepository = productRepository ?: ConstantProductRepository(),
    )
}