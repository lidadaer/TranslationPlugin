package cn.yiiguxing.plugin.translate.provider

import cn.yiiguxing.plugin.translate.Settings
import cn.yiiguxing.plugin.translate.documentation.TranslateDocumentationTask
import cn.yiiguxing.plugin.translate.util.Application
import cn.yiiguxing.plugin.translate.util.TranslateService
import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.lang.Language
import com.intellij.lang.documentation.DocumentationProviderEx
import com.intellij.lang.documentation.ExternalDocumentationProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiElement

/**
 * Translates documentation computed by another documentation provider. It should have
 *
 * order="first"
 *
 * in the extension declaration.
 */
class TranslatingDocumentationProvider : DocumentationProviderEx(), ExternalDocumentationProvider {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (!Settings.instance.translateDocumentation)
            return null

        return nullIfRecursive {
            val providerFromElement = DocumentationManager.getProviderFromElement(element, originalElement)
            val originalDoc = providerFromElement.generateDoc(element, originalElement)
            translate(originalDoc, element?.language)
        }
    }

    override fun fetchExternalDocumentation(
        project: Project?,
        element: PsiElement?,
        docUrls: MutableList<String>
    ): String? {

        if (!Settings.instance.translateDocumentation)
            return null

        return nullIfRecursive {
            val providerFromElement = Application.runReadAction(Computable {
                DocumentationManager.getProviderFromElement(element, null)
            })
            val originalDoc = when(providerFromElement) {
                is ExternalDocumentationProvider -> providerFromElement.fetchExternalDocumentation(project, element, docUrls)
                else -> null
            }

            translate(originalDoc, element?.language)
        }
    }

    override fun canPromptToConfigureDocumentation(element: PsiElement?): Boolean {
        return false
    }

    override fun promptToConfigureDocumentation(element: PsiElement?) {}

    //this method is deprecated and not used by the platform
    override fun hasDocumentationFor(element: PsiElement?, originalElement: PsiElement?): Boolean {
        return false
    }

    companion object {
        private val recursion = ThreadLocal.withInitial { 0 }

        private fun <T> nullIfRecursive(computation: () -> T?): T? {
            if (recursion.get() > 0)
                return null

            recursion.set(recursion.get() + 1)

            try {
                return computation()
            } finally {
                recursion.set(recursion.get() - 1)
            }
        }

        //to reuse long running translation task
        private var lastTranslation: TranslateDocumentationTask? = null

        private fun translate(text: String?, language: Language?): String? {
            if (text.isNullOrEmpty()) return null

            val lastTask = lastTranslation
            val translator = TranslateService.translator

            val task =
                if (lastTask != null && lastTask.translator.id == translator.id && lastTask.text == text) lastTask
                else TranslateDocumentationTask(text, language, translator)

            lastTranslation = task

            return task.nonBlockingGet()
        }
    }
}
