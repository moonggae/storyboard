package dev.bnorm.librettist.text

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import ch.deletescape.highlight.core.Highlighter
import ch.deletescape.highlight.highlight.render.AnnotatedStringRenderer
import dev.bnorm.librettist.LocalShowTheme
import dev.bnorm.librettist.ShowTheme
import dev.bnorm.librettist.text.antlr.kotlin.KotlinLexer
import dev.bnorm.librettist.text.antlr.kotlin.KotlinParser
import dev.bnorm.librettist.text.antlr.kotlin.KotlinParserBaseListener
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.tree.ParseTree
import org.antlr.v4.kotlinruntime.tree.ParseTreeWalker
import org.antlr.v4.kotlinruntime.tree.TerminalNode

@Composable
fun CodeText(
    code: String,
    language: String,
    modifier: Modifier = Modifier,
) {
    val showTheme = LocalShowTheme.current
    Column(modifier) {
        val highlighter = Highlighter { AnnotatedStringRenderer(showTheme.code) }
        for (line in code.lines()) {
            val result = highlighter.highlight(language, line, graceful = false)
            Text(text = result?.result ?: AnnotatedString(line))
        }
    }
}

@Composable
fun GradleKtsCodeText(
    text: String,
    modifier: Modifier = Modifier,
    identifierType: (String) -> SpanStyle? = { null },
) {
    val annotated = GradleKtsCodeString(text, identifierType)

    Column(modifier) {
        Text(text = annotated)
    }
}

@Composable
fun KotlinCodeText(
    text: String,
    modifier: Modifier = Modifier,
    identifierType: (String) -> SpanStyle? = { null },
) {
    val annotated = KotlinCodeString(text, identifierType)

    Column(modifier) {
        Text(text = annotated)
    }
}

@Composable
fun GroovyCodeText(
    text: String,
    modifier: Modifier = Modifier,
) {
    CodeText(text, "groovy", modifier)
}

@Composable
fun KotlinCodeString(
    text: String,
    identifierType: (String) -> SpanStyle? = { null },
): AnnotatedString {
    val codeStyle = LocalShowTheme.current.code
    val annotated = remember(text) { buildKotlinCodeString(text, codeStyle, identifierType) }
    return annotated
}

@Composable
fun GradleKtsCodeString(
    text: String,
    identifierType: (String) -> SpanStyle? = { null },
): AnnotatedString {
    val codeStyle = LocalShowTheme.current.code
    val annotated = remember(text) { buildGradleKtsCodeString(text, codeStyle, identifierType) }
    return annotated
}

fun buildKotlinCodeString(
    text: String,
    codeStyle: ShowTheme.CodeStyle,
    identifierType: (String) -> SpanStyle? = { null },
): AnnotatedString {
    return buildKotlinCodeString(text, { it.kotlinFile() }, codeStyle, identifierType)
}

fun buildGradleKtsCodeString(
    text: String,
    codeStyle: ShowTheme.CodeStyle,
    identifierType: (String) -> SpanStyle? = { null },
): AnnotatedString {
    return buildKotlinCodeString(text, { it.script() }, codeStyle, identifierType)
}

private fun buildKotlinCodeString(
    text: String,
    treeBuilder: (KotlinParser) -> ParseTree,
    codeStyle: ShowTheme.CodeStyle,
    identifierType: (String) -> SpanStyle? = { null },
): AnnotatedString {
    val walker = ParseTreeWalker()
    val lexer = KotlinLexer(CharStreams.fromString(text))
    val parser = KotlinParser(CommonTokenStream(lexer))
    val context = treeBuilder(parser)

    return buildAnnotatedString {
        withStyle(codeStyle.simple) { append(text) }

        walker.walk(object : KotlinParserBaseListener() {
            override fun enterAnnotation(ctx: KotlinParser.AnnotationContext) {
                addStyle(codeStyle.annotation, ctx.start!!.startIndex, ctx.stop!!.stopIndex + 1)
                super.enterAnnotation(ctx)
            }

            override fun enterLiteralConstant(ctx: KotlinParser.LiteralConstantContext) {
                addStyle(codeStyle.number, ctx.start!!.startIndex, ctx.stop!!.stopIndex + 1)
                super.enterLiteralConstant(ctx)
            }

            override fun visitTerminal(node: TerminalNode) {
                val symbol = node.symbol
                when (symbol.type) {
                    KotlinLexer.Tokens.FUN,
                    KotlinLexer.Tokens.VAL,
                    KotlinLexer.Tokens.VAR,
                    -> addStyle(codeStyle.keyword, symbol.startIndex, symbol.stopIndex + 1)

                    KotlinLexer.Tokens.QUOTE_OPEN,
                    KotlinLexer.Tokens.TRIPLE_QUOTE_OPEN,
                    KotlinLexer.Tokens.QUOTE_CLOSE,
                    KotlinLexer.Tokens.TRIPLE_QUOTE_CLOSE,
                    KotlinLexer.Tokens.LineStrText,
                    -> addStyle(codeStyle.string, symbol.startIndex, symbol.stopIndex + 1)

                    KotlinLexer.Tokens.LineStrRef,
                    -> addStyle(codeStyle.keyword, symbol.startIndex, symbol.startIndex + 1)

                    KotlinLexer.Tokens.Identifier -> {
                        val style = identifierType(node.text)
                        if (style != null) {
                            addStyle(style, symbol.startIndex, symbol.stopIndex + 1)
                        }
                    }
                }
            }
        }, context)
    }
}