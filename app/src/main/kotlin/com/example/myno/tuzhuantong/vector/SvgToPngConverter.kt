package com.example.myno.tuzhuantong.vector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.PathParser
import com.example.myno.tuzhuantong.diagnostics.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class SvgToPngConverter {

    companion object {
        private const val TAG = "SvgToPngConverter"
        private const val DEFAULT_SIZE = 512
        private const val MAX_SIZE = 4096
    }

    suspend fun convert(
        context: Context,
        inputStream: java.io.InputStream
    ): Bitmap {

        AppLogger.checkpoint(
            TAG,
            "========== SVG → PNG Converter 开始 =========="
        )

        val document = try {
            AppLogger.i(TAG, "阶段 1/4：开始解析 SVG XML")

            withContext(Dispatchers.IO) {
                parseSvg(inputStream)
            }
        } catch (e: Exception) {
            AppLogger.e(
                TAG,
                "阶段 1/4：SVG XML 解析失败",
                e
            )
            throw e
        }

        val root =
            document.documentElement
                ?: throw IllegalArgumentException(
                    "SVG 没有根节点"
                )

        if (!root.tagName.equals("svg", true)) {
            throw IllegalArgumentException(
                "文件不是有效的 SVG，根节点：${root.tagName}"
            )
        }

        AppLogger.i(
            TAG,
            "阶段 1/4：SVG XML 解析成功"
        )

        val svgInfo =
            parseSvgInfo(root)

        val bitmapWidth =
            svgInfo.width
                .roundToInt()
                .coerceIn(1, MAX_SIZE)

        val bitmapHeight =
            svgInfo.height
                .roundToInt()
                .coerceIn(1, MAX_SIZE)

        AppLogger.i(
            TAG,
            "SVG 尺寸=${svgInfo.width}×${svgInfo.height}"
        )

        AppLogger.i(
            TAG,
            "viewBox=${svgInfo.viewBoxMinX}," +
                "${svgInfo.viewBoxMinY}," +
                "${svgInfo.viewBoxWidth}," +
                "${svgInfo.viewBoxHeight}"
        )

        val bitmap =
            try {
                Bitmap.createBitmap(
                    bitmapWidth,
                    bitmapHeight,
                    Bitmap.Config.ARGB_8888
                )
            } catch (e: Exception) {
                AppLogger.e(
                    TAG,
                    "阶段 2/4：Bitmap 创建失败",
                    e
                )
                throw e
            }

        try {

            AppLogger.i(
                TAG,
                "阶段 3/4：开始 Canvas 渲染"
            )

            val canvas =
                Canvas(bitmap)

            bitmap.eraseColor(
                Color.TRANSPARENT
            )

            val scaleX =
                bitmapWidth.toFloat() /
                    svgInfo.viewBoxWidth

            val scaleY =
                bitmapHeight.toFloat() /
                    svgInfo.viewBoxHeight

            val scale =
                min(
                    scaleX,
                    scaleY
                )

            val renderedWidth =
                svgInfo.viewBoxWidth * scale

            val renderedHeight =
                svgInfo.viewBoxHeight * scale

            val offsetX =
                (bitmapWidth - renderedWidth) / 2f

            val offsetY =
                (bitmapHeight - renderedHeight) / 2f

            canvas.save()

            canvas.translate(
                offsetX,
                offsetY
            )

            canvas.scale(
                scale,
                scale
            )

            canvas.translate(
                -svgInfo.viewBoxMinX,
                -svgInfo.viewBoxMinY
            )

            val renderer =
                SvgCanvasRenderer(context)

            renderer.render(
                root,
                canvas
            )

            canvas.restore()

            AppLogger.i(
                TAG,
                "阶段 3/4：Canvas 渲染完成"
            )

            AppLogger.i(
                TAG,
                "渲染统计：" +
                    "path=${renderer.pathCount}, " +
                    "rect=${renderer.rectCount}, " +
                    "circle=${renderer.circleCount}, " +
                    "ellipse=${renderer.ellipseCount}, " +
                    "line=${renderer.lineCount}, " +
                    "polyline=${renderer.polylineCount}, " +
                    "polygon=${renderer.polygonCount}, " +
                    "text=${renderer.textCount}"
            )

        } catch (e: Exception) {

            AppLogger.e(
                TAG,
                "阶段 3/4：Canvas 渲染失败",
                e
            )

            try {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            } catch (recycleException: Exception) {
                AppLogger.w(
                    TAG,
                    "Bitmap 释放失败",
                    recycleException
                )
            }

            throw e
        }

        AppLogger.i(
            TAG,
            "阶段 4/4：Bitmap 生成成功，" +
                "${bitmap.width}×${bitmap.height}"
        )

        AppLogger.checkpoint(
            TAG,
            "========== SVG → PNG Converter 完成 =========="
        )

        return bitmap
    }

    private fun parseSvg(
        inputStream: java.io.InputStream
    ): org.w3c.dom.Document {

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

            return factory
                .newDocumentBuilder()
                .parse(stream)
        }
    }

    private data class SvgInfo(
        val width: Float,
        val height: Float,
        val viewBoxMinX: Float,
        val viewBoxMinY: Float,
        val viewBoxWidth: Float,
        val viewBoxHeight: Float
    )

    private fun parseSvgInfo(
        root: Element
    ): SvgInfo {

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
                width != null && width > 0f ->
                    width

                viewBox != null &&
                    viewBox.width > 0f ->
                    viewBox.width

                else ->
                    DEFAULT_SIZE.toFloat()
            }

        val finalHeight =
            when {
                height != null && height > 0f ->
                    height

                viewBox != null &&
                    viewBox.height > 0f ->
                    viewBox.height

                else ->
                    DEFAULT_SIZE.toFloat()
            }

        return SvgInfo(
            width = finalWidth,
            height = finalHeight,
            viewBoxMinX =
                viewBox?.minX ?: 0f,
            viewBoxMinY =
                viewBox?.minY ?: 0f,
            viewBoxWidth =
                max(
                    0.1f,
                    viewBox?.width
                        ?: finalWidth
                ),
            viewBoxHeight =
                max(
                    0.1f,
                    viewBox?.height
                        ?: finalHeight
                )
        )
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
                .split(
                    Regex("\\s+")
                )
                .mapNotNull {
                    it.toFloatOrNull()
                }

        if (numbers.size != 4) {
            return null
        }

        if (
            numbers[2] <= 0f ||
            numbers[3] <= 0f
        ) {
            return null
        }

        return ParsedViewBox(
            minX = numbers[0],
            minY = numbers[1],
            width = numbers[2],
            height = numbers[3]
        )
    }

    private fun parseDimension(
        value: String?
    ): Float? {

        if (value.isNullOrBlank()) {
            return null
        }

        return Regex(
            """[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?"""
        )
            .find(value.trim())
            ?.value
            ?.toFloatOrNull()
    }

    private class SvgCanvasRenderer(
        private val context: Context
    ) {

        var pathCount = 0
            private set

        var rectCount = 0
            private set

        var circleCount = 0
            private set

        var ellipseCount = 0
            private set

        var lineCount = 0
            private set

        var polylineCount = 0
            private set

        var polygonCount = 0
            private set

        var textCount = 0
            private set

        private val fillPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                    Paint.SUBPIXEL_TEXT_FLAG
            ).apply {
                style = Paint.Style.FILL
            }

        private val strokePaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style = Paint.Style.STROKE
            }

        private val textPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                    Paint.SUBPIXEL_TEXT_FLAG
            ).apply {
                style = Paint.Style.FILL
                isDither = true
            }

        fun render(
            root: Element,
            canvas: Canvas
        ) {

            renderChildren(
                root,
                canvas
            )
        }

        private fun renderChildren(
            element: Element,
            canvas: Canvas
        ) {

            val children =
                element.childNodes

            for (
                index in 0 until children.length
            ) {

                val node =
                    children.item(index)

                if (node !is Element) {
                    continue
                }

                renderElement(
                    node,
                    canvas
                )
            }
        }

        private fun renderElement(
            element: Element,
            canvas: Canvas
        ) {

            if (!isVisible(element)) {
                return
            }

            val tag =
                element.tagName
                    .substringAfterLast(':')
                    .lowercase()

            when (tag) {

                "g" -> {

                    canvas.save()

                    renderChildren(
                        element,
                        canvas
                    )

                    canvas.restore()
                }

                "path" -> {

                    drawPath(
                        element,
                        canvas
                    )

                    pathCount++
                }

                "rect" -> {

                    drawRect(
                        element,
                        canvas
                    )

                    rectCount++
                }

                "circle" -> {

                    drawCircle(
                        element,
                        canvas
                    )

                    circleCount++
                }

                "ellipse" -> {

                    drawEllipse(
                        element,
                        canvas
                    )

                    ellipseCount++
                }

                "line" -> {

                    drawLine(
                        element,
                        canvas
                    )

                    lineCount++
                }

                "polyline" -> {

                    drawPolyline(
                        element,
                        canvas,
                        false
                    )

                    polylineCount++
                }

                "polygon" -> {

                    drawPolyline(
                        element,
                        canvas,
                        true
                    )

                    polygonCount++
                }

                "text" -> {

                    drawText(
                        element,
                        canvas
                    )

                    textCount++
                }

                "tspan" -> {
                    // tspan 由父 text 统一处理
                }

                "defs",
                "style",
                "title",
                "desc" -> {
                    // 不直接绘制
                }

                else -> {

                    AppLogger.i(
                        TAG,
                        "跳过暂不支持的 SVG 元素：<$tag>"
                    )

                    renderChildren(
                        element,
                        canvas
                    )
                }
            }
        }

        private fun drawPath(
            element: Element,
            canvas: Canvas
        ) {

            val d =
                getAttribute(
                    element,
                    "d"
                )
                    ?: return

            if (d.isBlank()) {
                return
            }

            val path =
                try {
                    PathParser
                        .createPathFromPathData(d)
                } catch (e: Exception) {

                    AppLogger.w(
                        TAG,
                        "SVG path 解析失败",
                        e
                    )

                    null
                }
                    ?: return

            drawPathWithPaint(
                element,
                path,
                canvas
            )
        }

        private fun drawRect(
            element: Element,
            canvas: Canvas
        ) {

            val x =
                number(
                    element,
                    "x"
                )

            val y =
                number(
                    element,
                    "y"
                )

            val width =
                number(
                    element,
                    "width"
                )

            val height =
                number(
                    element,
                    "height"
                )

            if (
                width <= 0f ||
                height <= 0f
            ) {
                return
            }

            val rx =
                number(
                    element,
                    "rx"
                )

            val ry =
                number(
                    element,
                    "ry"
                )

            val path =
                Path()

            if (
                rx > 0f ||
                ry > 0f
            ) {

                val radiusX =
                    rx.coerceAtMost(
                        width / 2f
                    )

                val radiusY =
                    if (ry > 0f) {
                        ry.coerceAtMost(
                            height / 2f
                        )
                    } else {
                        radiusX
                    }

                path.addRoundRect(
                    RectF(
                        x,
                        y,
                        x + width,
                        y + height
                    ),
                    radiusX,
                    radiusY,
                    Path.Direction.CW
                )

            } else {

                path.addRect(
                    RectF(
                        x,
                        y,
                        x + width,
                        y + height
                    ),
                    Path.Direction.CW
                )
            }

            drawPathWithPaint(
                element,
                path,
                canvas
            )
        }

        private fun drawCircle(
            element: Element,
            canvas: Canvas
        ) {

            val cx =
                number(
                    element,
                    "cx"
                )

            val cy =
                number(
                    element,
                    "cy"
                )

            val r =
                number(
                    element,
                    "r"
                )

            if (r <= 0f) {
                return
            }

            val path =
                Path()

            path.addCircle(
                cx,
                cy,
                r,
                Path.Direction.CW
            )

            drawPathWithPaint(
                element,
                path,
                canvas
            )
        }

        private fun drawEllipse(
            element: Element,
            canvas: Canvas
        ) {

            val cx =
                number(
                    element,
                    "cx"
                )

            val cy =
                number(
                    element,
                    "cy"
                )

            val rx =
                number(
                    element,
                    "rx"
                )

            val ry =
                number(
                    element,
                    "ry"
                )

            if (
                rx <= 0f ||
                ry <= 0f
            ) {
                return
            }

            val path =
                Path()

            path.addOval(
                RectF(
                    cx - rx,
                    cy - ry,
                    cx + rx,
                    cy + ry
                ),
                Path.Direction.CW
            )

            drawPathWithPaint(
                element,
                path,
                canvas
            )
        }

        private fun drawLine(
            element: Element,
            canvas: Canvas
        ) {

            val path =
                Path()

            path.moveTo(
                number(element, "x1"),
                number(element, "y1")
            )

            path.lineTo(
                number(element, "x2"),
                number(element, "y2")
            )

            drawStrokeOnly(
                element,
                path,
                canvas
            )
        }

        private fun drawPolyline(
            element: Element,
            canvas: Canvas,
            close: Boolean
        ) {

            val points =
                getAttribute(
                    element,
                    "points"
                )
                    ?: return

            val values =
                Regex(
                    """[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?"""
                )
                    .findAll(points)
                    .mapNotNull {
                        it.value.toFloatOrNull()
                    }
                    .toList()

            if (values.size < 4) {
                return
            }

            val path =
                Path()

            path.moveTo(
                values[0],
                values[1]
            )

            var index = 2

            while (
                index + 1 < values.size
            ) {

                path.lineTo(
                    values[index],
                    values[index + 1]
                )

                index += 2
            }

            if (close) {
                path.close()
            }

            drawPathWithPaint(
                element,
                path,
                canvas
            )
        }

        /**
         * 真正的 SVG <text> 渲染。
         */
        private fun drawText(
            element: Element,
            canvas: Canvas
        ) {

            val text =
                collectText(
                    element
                )

            if (text.isEmpty()) {
                return
            }

            val x =
                number(
                    element,
                    "x"
                )

            val y =
                number(
                    element,
                    "y"
                )

            val fontSize =
                number(
                    element,
                    "font-size"
                )
                    .takeIf {
                        it > 0f
                    }
                    ?: 16f

            val fontWeight =
                getAttribute(
                    element,
                    "font-weight"
                )
                    ?.lowercase()
                    ?: "normal"

            val fontStyle =
                getAttribute(
                    element,
                    "font-style"
                )
                    ?.lowercase()
                    ?: "normal"

            val typeface =
                resolveTypeface(
                    getAttribute(
                        element,
                        "font-family"
                    ),
                    fontWeight,
                    fontStyle
                )

            val fill =
                resolveColor(
                    element,
                    "fill"
                )
                    ?: Color.BLACK

            val opacity =
                opacity(
                    getAttribute(
                        element,
                        "opacity"
                    )
                )

            val fillOpacity =
                opacity(
                    getAttribute(
                        element,
                        "fill-opacity"
                    )
                )

            textPaint.color =
                applyAlpha(
                    fill,
                    opacity * fillOpacity
                )

            textPaint.textSize =
                fontSize

            textPaint.typeface =
                typeface

            textPaint.isFakeBoldText =
                isBold(fontWeight)

            textPaint.textSkewX =
                if (isItalic(fontStyle)) {
                    -0.25f
                } else {
                    0f
                }

            textPaint.textAlign =
                when (
                    getAttribute(
                        element,
                        "text-anchor"
                    )
                        ?.lowercase()
                ) {

                    "middle" ->
                        Paint.Align.CENTER

                    "end" ->
                        Paint.Align.RIGHT

                    else ->
                        Paint.Align.LEFT
                }

            val letterSpacing =
                parseLetterSpacing(
                    getAttribute(
                        element,
                        "letter-spacing"
                    )
                )

            /*
             * Android Paint 没有直接提供 SVG
             * 的 letter-spacing 单位，所以这里
             * 手动逐字符绘制。
             */
            drawTextWithLetterSpacing(
                canvas = canvas,
                text = text,
                x = x,
                y = y,
                paint = textPaint,
                letterSpacing = letterSpacing
            )

            /*
             * 兼容 text 中嵌套 tspan。
             */
            val children =
                element.childNodes

            for (
                index in 0 until children.length
            ) {

                val node =
                    children.item(index)

                if (
                    node is Element &&
                    node.tagName.equals(
                        "tspan",
                        true
                    )
                ) {

                    drawTspan(
                        node,
                        canvas,
                        textPaint,
                        x,
                        y
                    )
                }
            }
        }

        private fun drawTspan(
            element: Element,
            canvas: Canvas,
            inheritedPaint: Paint,
            parentX: Float,
            parentY: Float
        ) {

            val text =
                collectText(
                    element
                )

            if (text.isEmpty()) {
                return
            }

            val paint =
                Paint(inheritedPaint)

            val x =
                getAttribute(
                    element,
                    "x"
                )
                    ?.let {
                        parseFloat(it)
                    }
                    ?: parentX

            val y =
                getAttribute(
                    element,
                    "y"
                )
                    ?.let {
                        parseFloat(it)
                    }
                    ?: parentY

            getAttribute(
                element,
                "font-size"
            )
                ?.let {
                    parseFloat(it)
                }
                ?.takeIf {
                    it > 0f
                }
                ?.let {
                    paint.textSize = it
                }

            getAttribute(
                element,
                "font-family"
            )
                ?.let {
                    paint.typeface =
                        resolveTypeface(
                            it,
                            getAttribute(
                                element,
                                "font-weight"
                            ) ?: "normal",
                            getAttribute(
                                element,
                                "font-style"
                            ) ?: "normal"
                        )
                }

            getAttribute(
                element,
                "fill"
            )
                ?.let {
                    resolveColorValue(it)
                }
                ?.let {
                    paint.color = it
                }

            val letterSpacing =
                parseLetterSpacing(
                    getAttribute(
                        element,
                        "letter-spacing"
                    )
                )

            drawTextWithLetterSpacing(
                canvas,
                text,
                x,
                y,
                paint,
                letterSpacing
            )
        }

        private fun drawTextWithLetterSpacing(
            canvas: Canvas,
            text: String,
            x: Float,
            y: Float,
            paint: Paint,
            letterSpacing: Float
        ) {

            if (
                letterSpacing == 0f ||
                text.length <= 1
            ) {

                canvas.drawText(
                    text,
                    x,
                    y,
                    paint
                )

                return
            }

            val widths =
                FloatArray(
                    text.length
                )

            paint.getTextWidths(
                text,
                widths
            )

            var totalWidth = 0f

            for (width in widths) {
                totalWidth += width
            }

            totalWidth +=
                letterSpacing *
                    (text.length - 1)

            val startX =
                when (paint.textAlign) {

                    Paint.Align.CENTER ->
                        x - totalWidth / 2f

                    Paint.Align.RIGHT ->
                        x - totalWidth

                    else ->
                        x
                }

            var currentX =
                startX

            for (index in text.indices) {

                val char =
                    text[index].toString()

                canvas.drawText(
                    char,
                    currentX,
                    y,
                    paint
                )

                currentX +=
                    widths[index] +
                        letterSpacing
            }
        }

        private fun collectText(
            element: Element
        ): String {

            val builder =
                StringBuilder()

            collectTextRecursive(
                element,
                builder
            )

            return builder
                .toString()
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim()
        }

        private fun collectTextRecursive(
            node: Node,
            builder: StringBuilder
        ) {

            val children =
                node.childNodes

            for (
                index in 0 until children.length
            ) {

                val child =
                    children.item(index)

                when {

                    child.nodeType ==
                        Node.TEXT_NODE -> {

                        builder.append(
                            child.nodeValue ?: ""
                        )
                    }

                    child is Element &&
                        child.tagName.equals(
                            "tspan",
                            true
                        ) -> {

                        collectTextRecursive(
                            child,
                            builder
                        )
                    }
                }
            }
        }

        private fun resolveTypeface(
            familyValue: String?,
            weight: String,
            style: String
        ): Typeface {

            val bold =
                isBold(weight)

            val italic =
                isItalic(style)

            val typefaceStyle =
                when {

                    bold && italic ->
                        Typeface.BOLD_ITALIC

                    bold ->
                        Typeface.BOLD

                    italic ->
                        Typeface.ITALIC

                    else ->
                        Typeface.NORMAL
                }

            val families =
                familyValue
                    ?.split(",")
                    ?.map {
                        it.trim()
                            .trim(
                                '"',
                                '\''
                            )
                    }
                    ?.filter {
                        it.isNotBlank()
                    }
                    ?: emptyList()

            for (family in families) {

                try {

                    val typeface =
                        Typeface.create(
                            family,
                            typefaceStyle
                        )

                    if (
                        typeface != null &&
                        !typeface.equals(
                            Typeface.DEFAULT
                        )
                    ) {
                        return typeface
                    }

                } catch (_: Exception) {
                }
            }

            /*
             * CSS generic family fallback。
             */
            val genericFamily =
                families
                    .firstOrNull {
                        it.equals(
                            "sans-serif",
                            true
                        ) ||
                            it.equals(
                                "serif",
                                true
                            ) ||
                            it.equals(
                                "monospace",
                                true
                            )
                    }

            if (genericFamily != null) {

                return Typeface.create(
                    genericFamily,
                    typefaceStyle
                )
            }

            return Typeface.create(
                "sans-serif",
                typefaceStyle
            )
        }

        private fun isBold(
            value: String
        ): Boolean {

            return value.equals(
                "bold",
                true
            ) ||
                value.equals(
                    "bolder",
                    true
                ) ||
                value.toIntOrNull()
                    ?.let {
                        it >= 600
                    }
                    ?: false
        }

        private fun isItalic(
            value: String
        ): Boolean {

            return value.equals(
                "italic",
                true
            ) ||
                value.equals(
                    "oblique",
                    true
                )
        }

        private fun parseLetterSpacing(
            value: String?
        ): Float {

            if (value.isNullOrBlank()) {
                return 0f
            }

            val text =
                value.trim()

            if (
                text.equals(
                    "normal",
                    true
                )
            ) {
                return 0f
            }

            return parseFloat(text)
        }

        private fun drawPathWithPaint(
            element: Element,
            path: Path,
            canvas: Canvas
        ) {

            val fill =
                resolveColor(
                    element,
                    "fill"
                )

            val stroke =
                resolveColor(
                    element,
                    "stroke"
                )

            val opacity =
                opacity(
                    getAttribute(
                        element,
                        "opacity"
                    )
                )

            val fillOpacity =
                opacity(
                    getAttribute(
                        element,
                        "fill-opacity"
                    )
                )

            val strokeOpacity =
                opacity(
                    getAttribute(
                        element,
                        "stroke-opacity"
                    )
                )

            if (
                fill != null &&
                fill != Color.TRANSPARENT
            ) {

                fillPaint.color =
                    applyAlpha(
                        fill,
                        opacity * fillOpacity
                    )

                canvas.drawPath(
                    path,
                    fillPaint
                )
            }

            if (
                stroke != null &&
                stroke != Color.TRANSPARENT
            ) {

                configureStrokePaint(
                    element,
                    stroke,
                    opacity * strokeOpacity
                )

                canvas.drawPath(
                    path,
                    strokePaint
                )
            }
        }

        private fun drawStrokeOnly(
            element: Element,
            path: Path,
            canvas: Canvas
        ) {

            val stroke =
                resolveColor(
                    element,
                    "stroke"
                )
                    ?: return

            if (
                stroke == Color.TRANSPARENT
            ) {
                return
            }

            val opacity =
                opacity(
                    getAttribute(
                        element,
                        "opacity"
                    )
                )

            val strokeOpacity =
                opacity(
                    getAttribute(
                        element,
                        "stroke-opacity"
                    )
                )

            configureStrokePaint(
                element,
                stroke,
                opacity * strokeOpacity
            )

            canvas.drawPath(
                path,
                strokePaint
            )
        }

        private fun configureStrokePaint(
            element: Element,
            color: Int,
            alphaMultiplier: Float
        ) {

            strokePaint.color =
                applyAlpha(
                    color,
                    alphaMultiplier
                )

            val width =
                number(
                    element,
                    "stroke-width"
                )

            strokePaint.strokeWidth =
                if (width > 0f) {
                    width
                } else {
                    1f
                }

            strokePaint.strokeCap =
                when (
                    getAttribute(
                        element,
                        "stroke-linecap"
                    )
                        ?.lowercase()
                ) {

                    "round" ->
                        Paint.Cap.ROUND

                    "square" ->
                        Paint.Cap.SQUARE

                    else ->
                        Paint.Cap.BUTT
                }

            strokePaint.strokeJoin =
                when (
                    getAttribute(
                        element,
                        "stroke-linejoin"
                    )
                        ?.lowercase()
                ) {

                    "round" ->
                        Paint.Join.ROUND

                    "bevel" ->
                        Paint.Join.BEVEL

                    else ->
                        Paint.Join.MITER
                }

            strokePaint.style =
                Paint.Style.STROKE
        }

        private fun resolveColor(
            element: Element,
            name: String
        ): Int? {

            val value =
                getAttribute(
                    element,
                    name
                )
                    ?: return null

            return resolveColorValue(
                value
            )
        }

        private fun resolveColorValue(
            value: String
        ): Int? {

            val text =
                value
                    .trim()
                    .lowercase()

            if (
                text == "none"
            ) {
                return Color.TRANSPARENT
            }

            if (
                text == "currentcolor"
            ) {
                return Color.BLACK
            }

            return try {

                when {

                    text.startsWith("#") ->
                        parseHexColor(text)

                    text.startsWith("rgb(") ->
                        parseRgb(text)

                    text.startsWith("rgba(") ->
                        parseRgba(text)

                    else ->
                        namedColor(text)
                }

            } catch (_: Exception) {
                null
            }
        }

        private fun parseHexColor(
            value: String
        ): Int? {

            val hex =
                value.removePrefix("#")

            return when (hex.length) {

                3 -> {

                    Color.rgb(
                        "${hex[0]}${hex[0]}"
                            .toInt(16),
                        "${hex[1]}${hex[1]}"
                            .toInt(16),
                        "${hex[2]}${hex[2]}"
                            .toInt(16)
                    )
                }

                4 -> {

                    Color.argb(
                        "${hex[3]}${hex[3]}"
                            .toInt(16),
                        "${hex[0]}${hex[0]}"
                            .toInt(16),
                        "${hex[1]}${hex[1]}"
                            .toInt(16),
                        "${hex[2]}${hex[2]}"
                            .toInt(16)
                    )
                }

                6 -> {

                    Color.rgb(
                        hex.substring(
                            0,
                            2
                        ).toInt(16),
                        hex.substring(
                            2,
                            4
                        ).toInt(16),
                        hex.substring(
                            4,
                            6
                        ).toInt(16)
                    )
                }

                8 -> {

                    /*
                     * SVG CSS 标准：
                     * #RRGGBBAA
                     */
                    Color.argb(
                        hex.substring(
                            6,
                            8
                        ).toInt(16),
                        hex.substring(
                            0,
                            2
                        ).toInt(16),
                        hex.substring(
                            2,
                            4
                        ).toInt(16),
                        hex.substring(
                            4,
                            6
                        ).toInt(16)
                    )
                }

                else ->
                    null
            }
        }

        private fun parseRgb(
            value: String
        ): Int? {

            val content =
                value
                    .substringAfter("(")
                    .substringBeforeLast(")")

            val parts =
                content
                    .replace(",", " ")
                    .split(
                        Regex("\\s+")
                    )
                    .filter {
                        it.isNotBlank()
                    }

            if (parts.size < 3) {
                return null
            }

            return Color.rgb(
                colorChannel(parts[0]),
                colorChannel(parts[1]),
                colorChannel(parts[2])
            )
        }

        private fun parseRgba(
            value: String
        ): Int? {

            val content =
                value
                    .substringAfter("(")
                    .substringBeforeLast(")")

            val parts =
                content
                    .replace(",", " ")
                    .split(
                        Regex("\\s+")
                    )
                    .filter {
                        it.isNotBlank()
                    }

            if (parts.size < 4) {
                return null
            }

            val alpha =
                parts[3]
                    .removeSuffix("%")
                    .toFloatOrNull()
                    ?: 1f

            val alphaValue =
                if (
                    parts[3].endsWith("%")
                ) {
                    alpha
                        .div(100f)
                        .coerceIn(0f, 1f)
                } else {
                    alpha.coerceIn(
                        0f,
                        1f
                    )
                }

            return Color.argb(
                (alphaValue * 255f)
                    .roundToInt(),
                colorChannel(parts[0]),
                colorChannel(parts[1]),
                colorChannel(parts[2])
            )
        }

        private fun colorChannel(
            value: String
        ): Int {

            val number =
                value
                    .removeSuffix("%")
                    .toFloatOrNull()
                    ?: 0f

            return if (
                value.endsWith("%")
            ) {

                (
                    number
                        .coerceIn(
                            0f,
                            100f
                        ) * 2.55f
                ).roundToInt()

            } else {

                number
                    .coerceIn(
                        0f,
                        255f
                    )
                    .roundToInt()
            }
        }

        private fun namedColor(
            value: String
        ): Int? {

            return when (value) {

                "black" ->
                    Color.BLACK

                "white" ->
                    Color.WHITE

                "red" ->
                    Color.RED

                "green" ->
                    Color.GREEN

                "blue" ->
                    Color.BLUE

                "yellow" ->
                    Color.YELLOW

                "cyan" ->
                    Color.CYAN

                "magenta" ->
                    Color.MAGENTA

                "gray",
                "grey" ->
                    Color.GRAY

                "orange" ->
                    Color.rgb(
                        255,
                        165,
                        0
                    )

                "purple" ->
                    Color.rgb(
                        128,
                        0,
                        128
                    )

                "transparent" ->
                    Color.TRANSPARENT

                else ->
                    null
            }
        }

        private fun applyAlpha(
            color: Int,
            multiplier: Float
        ): Int {

            val alpha =
                (
                    Color.alpha(color) *
                        multiplier.coerceIn(
                            0f,
                            1f
                        )
                )
                    .roundToInt()
                    .coerceIn(
                        0,
                        255
                    )

            return Color.argb(
                alpha,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
            )
        }

        private fun opacity(
            value: String?
        ): Float {

            if (value.isNullOrBlank()) {
                return 1f
            }

            val text =
                value.trim()

            val number =
                text
                    .removeSuffix("%")
                    .toFloatOrNull()
                    ?: return 1f

            return if (
                text.endsWith("%")
            ) {

                (number / 100f)
                    .coerceIn(
                        0f,
                        1f
                    )

            } else {

                number.coerceIn(
                    0f,
                    1f
                )
            }
        }

        private fun isVisible(
            element: Element
        ): Boolean {

            val display =
                getAttribute(
                    element,
                    "display"
                )

            if (
                display.equals(
                    "none",
                    true
                )
            ) {
                return false
            }

            val visibility =
                getAttribute(
                    element,
                    "visibility"
                )

            if (
                visibility.equals(
                    "hidden",
                    true
                )
            ) {
                return false
            }

            return true
        }

        private fun number(
            element: Element,
            name: String
        ): Float {

            return parseFloat(
                getAttribute(
                    element,
                    name
                )
            )
        }

        private fun parseFloat(
            value: String?
        ): Float {

            if (value.isNullOrBlank()) {
                return 0f
            }

            return Regex(
                """[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?"""
            )
                .find(value.trim())
                ?.value
                ?.toFloatOrNull()
                ?: 0f
        }

        private fun getAttribute(
            element: Element,
            name: String
        ): String? {

            if (
                element.hasAttribute(name)
            ) {
                return element
                    .getAttribute(name)
                    .takeIf {
                        it.isNotBlank()
                    }
            }

            /*
             * 兼容命名空间属性。
             */
            val attributes =
                element.attributes

            for (
                index in 0 until attributes.length
            ) {

                val node =
                    attributes.item(index)

                if (
                    node.localName.equals(
                        name,
                        true
                    )
                ) {
                    return node.nodeValue
                }
            }

            return null
        }
    }
}