package com.classpulse.service

import com.classpulse.config.AppProperties
import com.classpulse.web.ApiException
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.UUID

@Service
class StorageService(private val properties: AppProperties) {
    private val logger = LoggerFactory.getLogger(StorageService::class.java)
    val root: Path = Paths.get(properties.upload.dir).toAbsolutePath().normalize()

    @PostConstruct
    fun initialize() {
        Files.createDirectories(root)
        logger.info("作业图片目录: {}", root)
    }

    fun store(assignmentId: Long, bytes: ByteArray, extension: String): String {
        val relative = "assignments/$assignmentId/${UUID.randomUUID()}.$extension"
        val target = resolve(relative)
        Files.createDirectories(target.parent)
        Files.write(target, bytes, StandardOpenOption.CREATE_NEW)
        return relative
    }

    fun load(relativePath: String): Resource {
        val path = resolve(relativePath)
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw ApiException(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "图片文件不存在")
        }
        return FileSystemResource(path)
    }

    fun delete(relativePath: String) {
        runCatching {
            Files.deleteIfExists(resolve(relativePath))
        }.onFailure {
            logger.warn("无法删除旧作业图片 {}: {}", relativePath, it.message)
        }
    }

    fun resolve(relativePath: String): Path {
        val path = root.resolve(relativePath).normalize()
        if (!path.startsWith(root)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_PATH", "文件路径不合法")
        }
        return path
    }
}
