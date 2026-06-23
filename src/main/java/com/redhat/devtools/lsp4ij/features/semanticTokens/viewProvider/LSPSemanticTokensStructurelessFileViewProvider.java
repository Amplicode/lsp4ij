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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiReference;
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
        // Skip the file-level fallback stub returned when semantic tokens haven't loaded yet
        return (semanticToken != null && !semanticToken.isFileLevel()) ? semanticToken.getElement() : null;
    }

    /**
     * Filters out whole-file elements returned by super.findElementAt() for structureless files.
     * <p>
     * TextMate uses an {@code EmptyLexer} and a trivial parser, so its PSI tree consists of a single
     * leaf element spanning the entire file. Returning that element as the "element at offset" would
     * cause callers (e.g., {@code UsagePreviewPanel.getNameElementTextRange}) to treat the whole file
     * as a single named element and highlight everything. Return {@code null} instead so callers fall
     * back to their own range computation (e.g., the usage element's own text range).
     */
    @Nullable
    private PsiElement filterStructurelessSuperElement(@Nullable PsiElement superElement) {
        if (superElement == null) return null;
        PsiFile psiFile = getPsi(getBaseLanguage());
        if (psiFile != null && superElement.getTextRange().equals(psiFile.getTextRange())) {
            return null;
        }
        return superElement;
    }

    @Override
    public PsiElement findElementAt(int offset) {
        PsiElement element = getSemanticTokenElement(offset);
        return element != null ? element : filterStructurelessSuperElement(super.findElementAt(offset));
    }

    @Override
    public PsiElement findElementAt(int offset, @NotNull Class<? extends Language> lang) {
        PsiElement element = getSemanticTokenElement(offset);
        return element != null ? element : filterStructurelessSuperElement(super.findElementAt(offset, lang));
    }

    @Override
    public PsiElement findElementAt(int offset, @NotNull Language language) {
        PsiElement element = getSemanticTokenElement(offset);
        return element != null ? element : filterStructurelessSuperElement(super.findElementAt(offset, language));
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
