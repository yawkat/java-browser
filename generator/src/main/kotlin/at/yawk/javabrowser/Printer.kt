package at.yawk.javabrowser

import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.parser.Tag
import java.nio.file.Files
import java.nio.file.Path

/**
 * @author yawkat
 */
class Printer {
    val sourceFiles: MutableMap<String, AnnotatedSourceFile> = HashMap()
    val bindings: MutableMap<String, String> = HashMap()
    val types: MutableSet<String> = HashSet()

    fun registerBinding(binding: String, sourceFilePath: String) {
        bindings[binding] = sourceFilePath
    }

    fun registerType(type: String) {
        types.add(type)
    }

    fun addSourceFile(path: String, sourceFile: AnnotatedSourceFile) {
        sourceFiles[path] = sourceFile
    }

    fun print(root: Path) {
        for ((name, asf) in sourceFiles) {
            val generatedName = generatedName(name)
            val toRoot = generatedName.replace("[^/]+$".toRegex(), "").replace("[^/]+/".toRegex(), "../")

            fun toNode(annotation: SourceAnnotation, members: List<Node>): List<Node> {
                val o = when (annotation) {
                    is BindingRef -> {
                        val tgt = bindings[annotation.binding] ?: return members
                        Element(Tag.valueOf("a"), AnnotatedSourceFile.URI).also {
                            it.attr("href", toRoot + generatedName(tgt) + "#" + annotation.binding)
                        }
                    }
                    is BindingDecl -> {
                        Element(Tag.valueOf("a"), AnnotatedSourceFile.URI).also {
                            it.attr("id", annotation.binding)
                        }
                    }
                    is Style -> {
                        Element(Tag.valueOf("span"), AnnotatedSourceFile.URI).also {
                            it.attr("class", annotation.styleClass.joinToString(" "))
                        }
                    }
                }
                members.forEach { o.appendChild(it) }
                return listOf(o)
            }

            val document = Document.createShell(AnnotatedSourceFile.URI)

            val styleLink = document.head().appendElement("link")
            styleLink.attr("rel", "stylesheet")
            styleLink.attr("href", "$toRoot../code.css")

            val pre = document.body().appendElement("code").appendElement("pre")

            asf.toHtml(::toNode).forEach { pre.appendChild(it) }
            val to = root.resolve(generatedName)
            Files.createDirectories(to.parent)
            Files.write(to, document.html().toByteArray())
        }

        val byPackage = HashMap<String, MutableList<String>>()
        for (type in types) {
            if (type.isEmpty()) continue

            val sourceFilePath = bindings[type]!!
            var pkg = sourceFilePath
            while (!pkg.isEmpty()) {
                pkg = pkg.substring(0, pkg.lastIndexOf('/', pkg.length - 2) + 1)
                byPackage.computeIfAbsent(pkg, { ArrayList() }).add(type)
            }
        }

        val packageHtmlTemplate = Printer::class.java.getResourceAsStream("package.html").bufferedReader().readText()

        val objectMapper = ObjectMapper()
        for ((pkg, types) in byPackage) {
            val packageDir = root.resolve(pkg)
            val toFile = types.associate {
                val sourceFilePath = bindings[it]!!
                it to (generatedName(sourceFilePath.removePrefix(pkg)) + "#" + it)
            }
            Files.newOutputStream(packageDir.resolve("package.json")).use {
                objectMapper.writeValue(it, toFile)
            }
            Files.write(packageDir.resolve("index.html"), packageHtmlTemplate
                    .replace("##root##", pkg.replace("[^/]+/".toRegex(), "../"))
                    .replace("##package-name##", pkg.replace('/', '.'))
                    .toByteArray())
        }
    }

    private fun generatedName(name: String) = name.removeSuffix(".java") + ".html"
}