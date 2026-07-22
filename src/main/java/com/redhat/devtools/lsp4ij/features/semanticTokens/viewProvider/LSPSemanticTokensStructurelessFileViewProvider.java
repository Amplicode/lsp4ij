/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/

package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Semantic tokens-based file view provider for files with no inherent PSI tree where PSI element should correspond
 * exactly to semantic tokens, e.g., plain text and TextMate files.
 */
final class LSPSemanticTokensStructurelessFileViewProvider extends LSPSemanticTokensSingleRootFileViewProvider {

    LSPSemanticTokensStructurelessFileViewProvider(@NotNull PsiManager psiManager,
                                                   @NotNull VirtualFile virtualFile,
                                                   boolean eventSystemEnabled,
                                                   @NotNull Language language) {
        super(psiManager, virtualFile, eventSystemEnabled, language);
    }

    LSPSemanticTokensStructurelessFileViewProvider(@NotNull PsiManager psiManager,
                                                   @NotNull VirtualFile virtualFile,
                                                   boolean eventSystemEnabled) {
        super(psiManager, virtualFile, eventSystemEnabled);
    }

    @Override
    public boolean supportsIncrementalReparse(@NotNull Language rootLanguage) {
        // These files do not support incremental reparse
        if (isEnabled()) {
            return false;
        }
        return super.supportsIncrementalReparse(rootLanguage);
    }

    // NOTE: These are really the core of what makes this all work. Basically when any external caller needs to find an
    // element or reference for a given offset in the file, we use the semantic token information that was populated the
    // last time that semantic tokens were returned by the language server to return an element at that offset (or not).
    // In all cases, we take great care to delegate to the inherited behavior if we don't know for a fact that we
    // can/should respond ourselves. This ensures that non-LSP4IJ files see no change in behavior.

    @Nullable
    private PsiElement getSemanticTokenElement(int offset) {
        LSPSemanticToken semanticToken = isEnabled() ? getSemanticToken(offset) : null;
        // Only a concrete (non file-level) token carries the reference/declaration info needed by
        // hover and go-to-declaration. The file-level stub spans the whole file, so it's handled by
        // the narrow-element fallback below instead.
        return (semanticToken != null && !semanticToken.isFileLevel()) ? semanticToken.getElement() : null;
    }

    /**
     * Returns the element to expose at {@code offset} when no concrete semantic token is available.
     * <p>
     * TextMate/plain-text files are structureless: {@code super.findElementAt()} returns a single leaf
     * element spanning the ENTIRE file (empty lexer + trivial parser), and the file-level stub token
     * does the same. Neither can be exposed verbatim:
     * <ul>
     *   <li>Returning the whole-file element makes {@code UsagePreviewPanel#getNameElementTextRange}
     *       highlight the entire file in Find Usages.</li>
     *   <li>Returning {@code null} makes {@code EditorMouseHoverPopupManager#createContext} skip the
     *       documentation popup (so {@code textDocument/hover} is never sent) and leaves
     *       {@code GotoDeclarationHandler} without a source element (so go-to-declaration never runs).</li>
     * </ul>
     * So we synthesize a NON-null element with the word range at the offset (or {@code null} on
     * whitespace/punctuation, where hover/navigation shouldn't trigger anyway).
     */
    @Nullable
    private PsiElement narrowElementAt(int offset, @Nullable PsiElement superElement) {
        PsiFile psiFile = getPsi(getBaseLanguage());
        // A real (sub-file) super element is fine to return as-is.
        if (superElement != null && (psiFile == null || !superElement.getTextRange().equals(psiFile.getTextRange()))) {
            return superElement;
        }
        if (psiFile == null) {
            return null;
        }
        Document document = LSPIJUtils.getDocument(psiFile.getVirtualFile());
        if (document == null) {
            return null;
        }
        TextRange wordRange = LSPIJUtils.getWordRangeAt(document, psiFile, offset);
        if (wordRange == null) {
            return null;
        }
        // Build a synthetic (unknown-type) semantic-token element scoped to the word at the offset.
        // Using a real LSPSemanticTokenPsiElement (rather than a whole-file stub or a bare
        // LSPPsiElement) keeps it consistent with the platform symbol model and LSP4IJ's own
        // documentation provider, so the hover popup is built while Find Usages highlights only the
        // word — its range always contains the offset, so DeclarationOrReference#rangeWithOffset holds.
        return new LSPSemanticToken(psiFile, wordRange, null, null).getElement();
    }

    @Override
    public PsiElement findElementAt(int offset) {
        PsiElement element = getSemanticTokenElement(offset);
        return element != null ? element : narrowElementAt(offset, super.findElementAt(offset));
    }

    @Override
    public PsiElement findElementAt(int offset, @NotNull Class<? extends Language> lang) {
        PsiElement element = getSemanticTokenElement(offset);
        return element != null ? element : narrowElementAt(offset, super.findElementAt(offset, lang));
    }

    @Override
    public PsiElement findElementAt(int offset, @NotNull Language language) {
        PsiElement element = getSemanticTokenElement(offset);
        return element != null ? element : narrowElementAt(offset, super.findElementAt(offset, language));
    }

    @Override
    public PsiReference findReferenceAt(int offset) {
        PsiReference reference = getSemanticTokenReference(offset);
        return reference != null ? reference : super.findReferenceAt(offset);
    }

    @Override
    @Nullable
    public PsiReference findReferenceAt(int offset, @NotNull Language language) {
        PsiReference reference = getSemanticTokenReference(offset);
        return reference != null ? reference : super.findReferenceAt(offset, language);
    }
}
