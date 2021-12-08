package jp.tedo0627.highlightsections

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.startOffset
import java.awt.Color
import java.awt.Font

class HighlightAnnotator : Annotator {

    private val colorCode = mutableMapOf<String, Color>()
    private val effectCode = mutableMapOf<String, EffectType>()
    private val fontCode = mutableMapOf<String, Int>()

    init {
        fun add(key: String, color: Color) { colorCode[key] = color }
        fun add(key: String, effect: EffectType) { effectCode[key] = effect }
        fun add(key: String, font: Int) { fontCode[key] = font }

        add("0", Color(0, 0, 0))
        add("1", Color(0, 0, 170))
        add("2", Color(0, 170, 0))
        add("3", Color(0, 170, 170))
        add("4", Color(170, 0, 0))
        add("5", Color(170, 0, 170))
        add("6", Color(255, 170, 0))
        add("7", Color(170, 170, 170))
        add("8", Color(85, 85, 85))
        add("9", Color(85, 85, 255))
        add("a", Color(85, 255, 85))
        add("b", Color(85, 255, 255))
        add("c", Color(255, 85, 85))
        add("d", Color(255, 85, 255))
        add("e", Color(255, 255, 85))
        add("f", Color(255, 255, 255))
        add("g", Color(221, 214, 5))

        // k
        add("l", Font.BOLD)
        add("m", EffectType.STRIKEOUT)
        add("n", EffectType.LINE_UNDERSCORE)
        add("o", Font.ITALIC)
        // r
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val classList = mutableListOf(
            "PsiLiteralExpressionImpl", // java
            "KtLiteralStringTemplateEntry", // kotlin
            "StringLiteralExpressionImpl" // php
        )
        if (!classList.contains(element::class.java.simpleName)) return

        val text = element.text
        val isHereDoc = text.endsWith("\"\"\"")
        val suffixLength = text.length - when {
            isHereDoc -> 3
            text.endsWith("\"") -> 1
            else -> 0
        }

        if (!text.contains("§")) return
        val startOffset = element.startOffset

        val reset = mutableListOf<Int>()
        searchReset(text, "§r", reset, true, holder, startOffset)
        searchReset(text, "\n", reset, false, holder, startOffset)
        if (!isHereDoc) searchReset(text, "\\n", reset, false, holder, startOffset)

        var startIndex = 0
        var currentColor = Color.BLACK
        var currentFont = Font.PLAIN
        while (true) {
            val index = text.indexOf("§", startIndex, false)
            if (index == -1) break
            if (reset.contains(index)) {
                currentColor = Color.BLACK
                currentFont = Font.PLAIN
                startIndex = index + 1
                continue
            }

            startIndex = index + 1
            val key = text.getOrNull(index + 1)?.toString() ?: continue
            val endIndex = reset.firstOrNull { index < it } ?: suffixLength

            val color = colorCode[key]
            if (color != null) {
                currentColor = color
                lowlightSection(holder, startOffset + index, color)
                if (index + 2 >= endIndex) continue

                createAnnotation(holder, startOffset + index + 2, startOffset + endIndex, color, fontType = currentFont)
                continue
            }

            val effect = effectCode[key]
            if (effect != null) {
                lowlightSection(holder, startOffset + index)
                var searchIndex = index + 2
                var effectColor = currentColor
                while (true) {
                    if (searchIndex >= endIndex) break

                    val result = text.substring(searchIndex, endIndex).indexOf("§")
                    if (result == -1) {
                        createAnnotation(holder, startOffset + searchIndex, startOffset + endIndex, null, null, effectColor, effect, currentFont)
                        break
                    }

                    createAnnotation(holder, startOffset + searchIndex, startOffset + searchIndex + result, null, null, effectColor, effect, currentFont)

                    val colorKey = text.getOrNull(searchIndex + result + 1)?.toString()
                    val nextColor = if (colorKey != null) colorCode[colorKey] else null
                    if (nextColor != null) effectColor = nextColor

                    searchIndex += result + 2
                }
                continue
            }

            val font = fontCode[key]
            if (font != null) {
                currentFont = when {
                    currentFont == Font.BOLD && font == Font.ITALIC -> currentFont + font
                    currentFont == Font.ITALIC && font == Font.BOLD -> currentFont + font
                    else -> font
                }
                lowlightSection(holder, startOffset + index)
                createAnnotation(holder, startOffset + index + 2, startOffset + endIndex, fontType = currentFont)
            }
        }
    }

    private fun searchReset(text: String, search: String, resetList: MutableList<Int>, isLowlight: Boolean, holder: AnnotationHolder, startOffset: Int) {
        var startIndex = 0
        while (true) {
            val index = text.indexOf(search, startIndex, false)
            if (index == -1) break

            resetList.add(index)
            startIndex = index + 1
            if (isLowlight) lowlightSection(holder, startOffset + index)
        }
    }

    private fun lowlightSection(holder: AnnotationHolder, startIndex: Int, color: Color? = null) {
        createAnnotation(holder, startIndex, startIndex + 2, color, Color(0x3C3F41))
    }

    private fun createAnnotation(
        holder: AnnotationHolder,
        startIndex: Int,
        endIndex: Int,
        foregroundColor: Color? = null,
        backgroundColor: Color? = null,
        effectColor: Color? = null,
        effectType: EffectType? = null,
        fontType: Int = Font.PLAIN
    ) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(startIndex, endIndex))
            .enforcedTextAttributes(TextAttributes(foregroundColor, backgroundColor, effectColor, effectType, fontType))
            .create()
    }
}