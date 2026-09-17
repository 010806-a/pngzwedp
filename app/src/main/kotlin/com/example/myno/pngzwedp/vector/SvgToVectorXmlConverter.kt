package com.example.myno.pngzwedp.vector

import android.graphics.Color
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

class SvgToVectorXmlConverter {

    fun convert(
        inputStream: InputStream
    ): String {

        inputStream.use { stream ->

            val factory =
                DocumentBuilderFactory
                    .newInstance()
                    .apply {
                        isNamespaceAware = true
                        try {
                            setFeature(
                                "http://apache.org/xml/features/disallow-doctype-decl",
                                true
                            )
                        } catch (_: Exception) {
                        }
                    }

            val document =
                factory
                    .newDocumentBuilder()
                    .parse(stream)

            val root =
                document.documentElement

            if (
                root == null ||
                root.tagName.lowercase() != "svg"
            ) {
                throw IllegalArgumentException(
                    "文件不是有效的 SVG"
                )
            }

            val width =
                getSvgSize(
                    root,
                    "width",
                    24f
                )

            val height =
                getSvgSize(
                    root,
                    "height",
                    24f
                )

            val viewBox =
                root.getAttribute(
                    "viewBox"
                ).trim()

            val viewport =
                parseViewBox(
                    viewBox,
                    width,
                    height
                )

            val builder =
                StringBuilder()

            builder.append(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            )

            builder.append(
                "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
            )

            builder.append(
                "    android:width=\"${formatDp(width)}dp\"\n"
            )

            builder.append(
                "    android:height=\"${formatDp(height)}dp\"\n"
            )

            builder.append(
                "    android:viewportWidth=\"${formatNumber(viewport.width)}\"\n"
            )

            builder.append(
                "    android:viewportHeight=\"${formatNumber(viewport.height)}\">\n"
            )

            appendChildren(
                element = root,
                builder = builder,
                indent = "    "
            )

            builder.append(
                "</vector>\n"
            )

            return builder.toString()
        }
    }

    private fun appendChildren(
        element: Element,
        builder: StringBuilder,
        indent: String
    ) {

        val children =
            element.childNodes

        for (index in 0 until children.length) {

            val node =
                children.item(index)

            if (node.nodeType != Node.ELEMENT_NODE) {
                continue
            }

            val child =
                node as Element

            when (
                child.tagName
                    .lowercase()
            ) {

                "path" -> {
                    appendPath(
                        child,
                        builder,
                        indent
                    )
                }

                "g" -> {
                    appendChildren(
                        child,
                        builder,
                        indent
                    )
                }

                "rect" -> {
                    appendRect(
                        child,
                        builder,
                        indent
                    )
                }

                "circle" -> {
                    appendCircle(
                        child,
                        builder,
                        indent
                    )
                }

                "ellipse" -> {
                    appendEllipse(
                        child,
                        builder,
                        indent
                    )
                }

                "line" -> {
                    appendLine(
                        child,
                        builder,
                        indent
                    )
                }

                "polyline",
                "polygon" -> {
                    appendPolygon(
                        child,
                        builder,
                        indent
                    )
                }
            }
        }
    }

    private fun appendPath(
        element: Element,
        builder: StringBuilder,
        indent: String
    ) {

        val pathData =
            element.getAttribute("d")
                .trim()

        if (pathData.isEmpty()) {
            return
        }

        builder.append(
            indent
        )

        builder.append(
            "<path\n"
        )

        builder.append(
            indent
        )

        builder.append(
            "    android:pathData=\""
        )

        builder.append(
            escapeXmlAttribute(
                pathData
            )
        )

        builder.append(
            "\""
        )

        appendFill(
            element,
            builder,
            indent
        )

        appendStroke(
            element,
            builder,
            indent
        )

        builder.append(
            " />\n"
        )
    }

    private fun appendRect(
        element: Element,
        builder: StringBuilder,
        indent: String
    ) {

        val x =
            getFloat(
                element,
                "x",
                0f
            )

        val y =
            getFloat(
                element,
                "y",
                0f
            )

        val width =
            getFloat(
                element,
                "width",
                0f
            )

        val height =
            getFloat(
                element,
                "height",
                0f
            )

        if (width <= 0f || height <= 0f) {
            return
        }

        val path =
            buildString {
                append("M")
                append(formatNumber(x))
                append(",")
                append(formatNumber(y))

                append(" H")
                append(formatNumber(x + width))

                append(" V")
                append(formatNumber(y + height))

                append(" H")
                append(formatNumber(x))

                append(" Z")
            }

        appendSimplePath(
            element,
            path,
            builder,
            indent
        )
    }

    private fun appendCircle(
        element: Element,
        builder: StringBuilder,
        indent: String
    ) {

        val cx =
            getFloat(
                element,
                "cx",
                0f
            )

        val cy =
            getFloat(
                element,
                "cy",
                0f
            )

        val radius =
            getFloat(
                element,
                "r",
                0f
            )

        if (radius <= 0f) {
            return
        }

        val path =
            buildString {

                append("M")
                append(formatNumber(cx - radius))
                append(",")
                append(formatNumber(cy))

                append("a")
                append(formatNumber(radius))
                append(",")
                append(formatNumber(radius))
                append(" 0 1,0 ")
                append(formatNumber(radius * 2))
                append(",0")

                append("a")
                append(formatNumber(radius))
                append(",")
                append(formatNumber(radius))
                append(" 0 1,0 ")
                append(formatNumber(-radius * 2))
                append(",0")
            }

        appendSimplePath(
            element,
            path,
            builder,
            indent
        )
    }

    private fun appendEllipse(
        element: Element,
        builder: StringBuilder,
        indent: String
    ) {

        val cx =
            getFloat(
                element,
                "cx",
                0f
            )

        val cy =
            getFloat(
                element,
                "cy",
                0f
            )

        val rx =
            getFloat(
                element,
                "rx",
                0f
            )

        val ry =
            getFloat(
                element,
                "ry",
                0f
            )

        if (rx <= 0f || ry <= 0f) {
            return
        }

        val path =
            buildString {

                append("M")
                append(formatNumber(cx - rx))
                append(",")
                append(formatNumber(cy))

                append("a")
                append(formatNumber(rx))
                append(",")
                append(formatNumber(ry))
                append(" 0 1,0 ")
                append(formatNumber(rx * 2))
                append(",0")

                append("a")
                append(formatNumber(rx))
                append(",")
                append(formatNumber(ry))
                append(" 0 1,0 ")
                append(formatNumber(-rx * 2))
                append(",0")
            }

        appendSimplePath(
            element,
            path,
            builder,
            indent
        )
    }

    private fun appendLine(
        element: Element,
        builder: StringBuilder,
        indent: String
    ) {

        val x1 =
            getFloat(
                element,
                "x1",
                0f
            )

        val y1 =
            getFloat(
                element,
                "y1",
                0f
            )

        val x2 =
            getFloat(
                element,
                "x2",
                0f
            )

        val y2 =
            getFloat(
                element,
                "y2",
                0f
            )

        val path =
            "M${formatNumber(x1)},${formatNumber(y1)} L${formatNumber(x2)},${formatNumber(y2)}"

        appendSimplePath(
            element,
            path,
            builder,
            indent
        )
    }

    private fun appendPolygon(
        element: Element,
        builder: StringBuilder,
        indent: String
    ) {

        val points =
            element.getAttribute(
                "points"
            ).trim()

        if (points.isEmpty()) {
            return
        }

        val numbers =
            points
                .replace(",", " ")
                .trim()
                .split(
                    Regex("\\s+")
                )
                .mapNotNull {
                    it.toFloatOrNull()
                }

        if (numbers.size < 4) {
            return
        }

        val path =
            StringBuilder()

        path.append("M")
        path.append(
            formatNumber(numbers[0])
        )
        path.append(",")
        path.append(
            formatNumber(numbers[1])
        )

        var index = 2

        while (index + 1 < numbers.size) {

            path.append(" L")
            path.append(
                formatNumber(numbers[index])
            )
            path.append(",")
            path.append(
                formatNumber(numbers[index + 1])
            )

            index += 2
        }

        if (
            element.tagName
                .lowercase() == "polygon"
        ) {
            path.append(" Z")
        }

        appendSimplePath(
            element,
            path.toString(),
            builder,
            indent
        )
    }

    private fun appendSimplePath(
        element: Element,
        pathData: String,
        builder: StringBuilder,
        indent: String
    ) {

        builder.append(indent)
        builder.append("<path\n")
        builder.append(indent)
        builder.append("    android:pathData=\"")
        builder.append(
            escapeXmlAttribute(
                pathData
            )
        )
        builder.append("\"")

        appendFill(
            element,
            builder,
            indent
        )

        appendStroke(
            element,
            builder,
            indent
        )

        builder.append(" />\n")
    }

    private fun appendFill(
        element: Element,
        builder: StringBuilder,
        indent: String
    ) {

        val fill =
            element.getAttribute(
                "fill"
            ).trim()

        if (
            fill.isNotEmpty() &&
            !fill.equals(
                "none",
                ignoreCase = true
            )
        ) {

            val color =
                convertColor(
                    fill
                )

            if (color != null) {

                builder.append("\n")
                builder.append(indent)
                builder.append(
                    "    android:fillColor=\""
                )
                builder.append(color)
                builder.append("\"")
            }
        }
    }

    private fun appendStroke(
        element: Element,
        builder: StringBuilder,
        indent: String
    ) {

        val stroke =
            element.getAttribute(
                "stroke"
            ).trim()

        if (
            stroke.isEmpty() ||
            stroke.equals(
                "none",
                ignoreCase = true
            )
        ) {
            return
        }

        val color =
            convertColor(
                stroke
            )
            ?: return

        builder.append("\n")
        builder.append(indent)
        builder.append(
            "    android:strokeColor=\""
        )
        builder.append(color)
        builder.append("\"")

        val width =
            element.getAttribute(
                "stroke-width"
            ).trim()

        if (width.isNotEmpty()) {

            builder.append("\n")
            builder.append(indent)
            builder.append(
                "    android:strokeWidth=\""
            )
            builder.append(
                formatNumber(
                    parseNumber(width)
                )
            )
            builder.append("\"")
        }

        val lineCap =
            element.getAttribute(
                "stroke-linecap"
            ).trim()

        if (lineCap.isNotEmpty()) {

            val value =
                when (
                    lineCap.lowercase()
                ) {

                    "round" -> "round"
                    "square" -> "square"
                    else -> "butt"
                }

            builder.append("\n")
            builder.append(indent)
            builder.append(
                "    android:strokeLineCap=\""
            )
            builder.append(value)
            builder.append("\"")
        }

        val lineJoin =
            element.getAttribute(
                "stroke-linejoin"
            ).trim()

        if (lineJoin.isNotEmpty()) {

            val value =
                when (
                    lineJoin.lowercase()
                ) {

                    "round" -> "round"
                    "bevel" -> "bevel"
                    else -> "miter"
                }

            builder.append("\n")
            builder.append(indent)
            builder.append(
                "    android:strokeLineJoin=\""
            )
            builder.append(value)
            builder.append("\"")
        }
    }

    private fun convertColor(
        value: String
    ): String? {

        val color =
            value.trim()

        if (
            color.equals(
                "none",
                ignoreCase = true
            )
        ) {
            return null
        }

        if (
            color.startsWith("#")
        ) {

            return when (
                color.length
            ) {

                4 -> {
                    val r = color[1]
                    val g = color[2]
                    val b = color[3]

                    "#FF$r$r$g$g$b$b"
                }

                5 -> {
                    color
                }

                7 -> {
                    color
                }

                9 -> {
                    color
                }

                else -> null
            }
        }

        return when (
            color.lowercase()
        ) {

            "black" -> "#FF000000"
            "white" -> "#FFFFFFFF"
            "red" -> "#FFFF0000"
            "green" -> "#FF008000"
            "blue" -> "#FF0000FF"
            "yellow" -> "#FFFFFF00"
            "cyan" -> "#FF00FFFF"
            "magenta" -> "#FFFF00FF"
            "gray",
            "grey" -> "#FF808080"

            else -> {

                if (
                    color.startsWith(
                        "rgb(",
                        ignoreCase = true
                    )
                ) {
                    parseRgb(
                        color
                    )
                } else {
                    null
                }
            }
        }
    }

    private fun parseRgb(
        value: String
    ): String? {

        val content =
            value
                .substringAfter("(")
                .substringBeforeLast(")")

        val parts =
            content
                .split(",")
                .map {
                    it.trim()
                }

        if (parts.size != 3) {
            return null
        }

        val r =
            parts[0]
                .toIntOrNull()
                ?: return null

        val g =
            parts[1]
                .toIntOrNull()
                ?: return null

        val b =
            parts[2]
                .toIntOrNull()
                ?: return null

        return String.format(
            "#%08X",
            Color.rgb(
                r,
                g,
                b
            )
        )
    }

    private fun getSvgSize(
        root: Element,
        attribute: String,
        defaultValue: Float
    ): Float {

        val value =
            root.getAttribute(
                attribute
            ).trim()

        if (value.isEmpty()) {
            return defaultValue
        }

        return parseNumber(
            value
        ).takeIf {
            it > 0f
        } ?: defaultValue
    }

    private fun getFloat(
        element: Element,
        attribute: String,
        defaultValue: Float
    ): Float {

        val value =
            element.getAttribute(
                attribute
            ).trim()

        if (value.isEmpty()) {
            return defaultValue
        }

        return parseNumber(
            value
        )
    }

    private fun parseNumber(
        value: String
    ): Float {

        val match =
            Regex(
                "[-+]?(?:\\d*\\.\\d+|\\d+\\.?)(?:[eE][-+]?\\d+)?"
            ).find(value)

        return match
            ?.value
            ?.toFloatOrNull()
            ?: 0f
    }

    private data class ViewBox(
        val width: Float,
        val height: Float
    )

    private fun parseViewBox(
        value: String,
        fallbackWidth: Float,
        fallbackHeight: Float
    ): ViewBox {

        if (value.isEmpty()) {
            return ViewBox(
                fallbackWidth,
                fallbackHeight
            )
        }

        val values =
            value
                .replace(",", " ")
                .trim()
                .split(
                    Regex("\\s+")
                )
                .mapNotNull {
                    it.toFloatOrNull()
                }

        if (values.size != 4) {
            return ViewBox(
                fallbackWidth,
                fallbackHeight
            )
        }

        return ViewBox(
            width = values[2].takeIf {
                it > 0f
            } ?: fallbackWidth,

            height = values[3].takeIf {
                it > 0f
            } ?: fallbackHeight
        )
    }

    private fun formatDp(
        value: Float
    ): String {
        return formatNumber(
            value
        )
    }

    private fun formatNumber(
        value: Float
    ): String {

        if (value == 0f) {
            return "0"
        }

        return if (
            value == value.toInt().toFloat()
        ) {
            value.toInt().toString()
        } else {
            "%.3f".format(
                java.util.Locale.US,
                value
            )
                .trimEnd('0')
                .trimEnd('.')
        }
    }

    private fun escapeXmlAttribute(
        value: String
    ): String {

        return value
            .replace(
                "&",
                "&amp;"
            )
            .replace(
                "\"",
                "&quot;"
            )
            .replace(
                "<",
                "&lt;"
            )
            .replace(
                ">",
                "&gt;"
            )
    }
}