package at.yawk.javabrowser.server.view

import at.yawk.javabrowser.AnnotatedSourceFile
import at.yawk.javabrowser.ArtifactMetadata
import at.yawk.javabrowser.BindingDecl
import at.yawk.javabrowser.BindingRef
import at.yawk.javabrowser.LocalVariableRef
import at.yawk.javabrowser.SourceAnnotation
import at.yawk.javabrowser.Style
import at.yawk.javabrowser.server.BindingResolver
import at.yawk.javabrowser.server.appendChildren
import at.yawk.javabrowser.server.artifact.ArtifactNode
import com.google.common.collect.Iterators
import org.apache.commons.text.StringEscapeUtils
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Tag
import java.lang.AssertionError
import java.lang.IllegalStateException

/**
 * @author yawkat
 */
@Suppress("unused")
class SourceFileView(
        val artifactId: ArtifactNode,
        private val classpath: Set<String>,
        val sourceFilePathDir: String,
        val sourceFilePathFile: String,
        val alternatives: List<Alternative>,
        val artifactMetadata: ArtifactMetadata,

        private val bindingResolver: BindingResolver,
        private val sourceFile: AnnotatedSourceFile
) : View("source-file.ftl") {

    val codeHtml by lazy {
        val doc = Document("")
        doc.outputSettings().prettyPrint(false)
        val pre = doc.appendElement("pre")
        pre.appendChildren(sourceFile.toHtml(::toNode))
        pre.html()!! // inner HTML, we need the pre so jsoup properly formats
    }

    val declarations: Iterator<DeclarationNode>
        get() {
            val flat = Iterators.peekingIterator(sourceFile.declarations)

            class SubListItr(val parent: String?) : Iterator<DeclarationNode> {
                var eaten = false
                var toEat: SubListItr? = null

                override fun hasNext(): Boolean {
                    if (eaten) throw IllegalStateException("Iterator eaten. Parent is: $parent")

                    // consume the toEat iterator so we're sure we reached the end of the previous subtree
                    toEat?.forEach { _ -> }
                    toEat?.eaten = true
                    toEat = null

                    if (!flat.hasNext()) return false
                    if (flat.peek().parent == parent) return true
                    // sanity check. The topmost iterator should consume all elements
                    if (parent == null) throw AssertionError()
                    return false
                }

                override fun next(): DeclarationNode {
                    val item = flat.next()
                    assert(item.parent == parent) // checked in hasNext
                    val newItr = SubListItr(item.binding)
                    toEat = newItr
                    return DeclarationNode(item, newItr)
                }
            }

            return SubListItr(null)
        }

    private fun toNode(annotation: SourceAnnotation, members: List<Node>): List<Node> = when (annotation) {
        is BindingRef -> {
            linkToBinding(members, annotation.binding, annotation.id)
        }
        is BindingDecl -> {
            val showDeclaration = when (annotation.description) {
                is BindingDecl.Description.Initializer -> false
                else -> true
            }

            val link = Element(Tag.valueOf("a"), AnnotatedSourceFile.URI)
            link.attr("id", annotation.binding)
            link.attr("href", BindingResolver.bindingHash(annotation.binding))
            link.appendChildren(members)

            if (showDeclaration) {
                val moreInfo = Element(Tag.valueOf("a"), AnnotatedSourceFile.URI)
                moreInfo.attr("class", "show-refs")

                val superHtml = if (!annotation.superBindings.isEmpty()) {
                    val superList = Element(Tag.valueOf("ul"), AnnotatedSourceFile.URI)
                    for (superBinding in annotation.superBindings) {
                        val entry = superList.appendElement("li")
                        entry.appendChildren(linkToBinding(
                                listOf(TextNode(superBinding.name, AnnotatedSourceFile.URI)),
                                superBinding.binding,
                                refId = null
                        ))
                    }
                    StringEscapeUtils.escapeEcmaScript(superList.html())
                } else {
                    ""
                }
                moreInfo.attr("href", "javascript:showReferences('${annotation.binding}', '$superHtml')")

                listOf(moreInfo, link)
            } else {
                listOf(link)
            }
        }
        is Style -> listOf(Element(Tag.valueOf("span"), AnnotatedSourceFile.URI).also {
            it.attr("class", annotation.styleClass.joinToString(" "))
            it.appendChildren(members)
        })
        is LocalVariableRef -> listOf(Element(Tag.valueOf("span"), AnnotatedSourceFile.URI).also {
            it.attr("class", "local-variable")
            it.attr("data-local-variable", annotation.id)
            it.appendChildren(members)
        })
    }

    private fun linkToBinding(members: List<Node>,
                              binding: String,
                              refId: Int?): List<Node> {
        val uris = bindingResolver.resolveBinding(classpath, binding)
        return if (uris.isEmpty()) {
            members
        } else {
            listOf(Element(Tag.valueOf("a"), AnnotatedSourceFile.URI).also {
                it.attr("href", uris[0].toASCIIString())
                if (refId != null) it.attr("id", "ref-$refId")
                it.appendChildren(members)
            })
        }
    }

    data class Alternative(
            val artifactId: String,
            val sourceFilePath: String
    )
}
