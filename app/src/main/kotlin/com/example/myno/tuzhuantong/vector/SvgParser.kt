package com.example.myno.tuzhuantong.vector

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory
import java.io.InputStream
import kotlin.math.max

/**
 * SVG 基础信息解析器。
 *
 * 主要负责：
 * 1. 验证 SVG 根节点
 * 2. 获取 width / height
 * 3. 获取 viewBox
 * 4. 在没有明确尺寸时，根据 viewBox 推导尺寸
 */
class SvgParser {

    data class SvgInfo(
        val width: Float,
        val height: Float,
        val viewBoxMinX: Float = 0f,
        val viewBoxMinY: Float = 0f,
        val viewBoxWidth: Float = width,
        val viewBoxHeight: Float = height
    )

    fun parse(inputStream: InputStream): SvgInfo {
        inputStream.use { stream ->

            val factory =
                DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true

                    try {
                        setFeature(
                            "http://apache.org/xml/features/disallow-doctype-decl",
                            true
                        )
                    } catch (_: Exception) {
                    }

                    try {
                        setFeature(
                            "http://xml.org/sax/features/external-general-entities",
                            false
                        )
                    } catch (_: Exception) {
                    }

                    try {
                        setFeature(
                            "http://xml.org/sax/features/external-parameter-entities",
                            false
                        )
                    } catch (_: Exception) {
                    }

                    try {
                        setFeature(
                            "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                            false
                        )
                    } catch (_: Exception) {
                    }

                    try {
                        setXIncludeAware(false)
                    } catch (_: Exception) {
                    }

                    try {
                        isExpandEntityReferences = false
                    } catch (_: Exception) {
                    }
                }

            val document =
                factory
                    .newDocumentBuilder()
                    .parse(stream)

            val root = document.documentElement
                ?: throw IllegalArgumentException("SVG 没有根节点")

            if (!root.tagName.equals("svg", ignoreCase = true)) {
                throw IllegalArgumentException("文件不是有效的 SVG")
            }

            val viewBox =
                parseViewBox(
                    root.getAttribute("viewBox")
                )

            val width =
                parseDimension(
                    root.getAttribute("width")
                )

            val height =
                parseDimension(
                    root.getAttribute("height")
                )

            val finalWidth =
                when {
                    width != null && width > 0f -> width
                    viewBox != null && viewBox.width > 0f ->
                        viewBox.width
                    else -> 24f
                }

            val finalHeight =
                when {
                    height != null && height > 0f -> height
                    viewBox != null && viewBox.height > 0f ->
                        viewBox.height
                    else -> 24f
                }

            val viewportWidth =
                viewBox?.width?.takeIf { it > 0f }
                    ?: finalWidth

            val viewportHeight =
                viewBox?.height?.takeIf { it > 0f }
                    ?: finalHeight

            return SvgInfo(
                width = finalWidth,
                height = finalHeight,
                viewBoxMinX = viewBox?.minX ?: 0f,
                viewBoxMinY = viewBox?.minY ?: 0f,
                viewBoxWidth = max(0.1f, viewportWidth),
                viewBoxHeight = max(0.1f, viewportHeight)
            )
        }
    }

    private data class ParsedViewBox(
        val minX: Float,
        val minY: Float,
        val width: Float,
        val height: Float
    )

    private fun parseViewBox(
        value: String?
    ): ParsedViewBox? {

        if (value.isNullOrBlank()) {
            return null
        }

        val numbers =
            value
                .trim()
                .replace(",", " ")
                .split(Regex("\\s+"))
                .mapNotNull { it.toFloatOrNull() }

        if (numbers.size != 4) {
            return null
        }

        val width = numbers[2]
        val height = numbers[3]

        if (width <= 0f || height <= 0f) {
            return null
        }

        return ParsedViewBox(
            minX = numbers[0],
            minY = numbers[1],
            width = width,
            height = height
        )
    }

    private fun parseDimension(
        value: String?
    ): Float? {

        if (value.isNullOrBlank()) {
            return null
        }

        val text = value.trim()

        val match =
            Regex(
                """[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?"""
            ).find(text)
                ?: return null

        return match.value.toFloatOrNull()
    }
}