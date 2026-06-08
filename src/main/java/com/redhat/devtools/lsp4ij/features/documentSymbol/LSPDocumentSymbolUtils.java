/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentSymbol;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.documentSymbol.LSPDocumentSymbolStructureViewModel.LSPDocumentSymbolViewElement;
import com.redhat.devtools.lsp4ij.features.documentSymbol.LSPDocumentSymbolStructureViewModel.LSPFileStructureViewElement;
import com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider.LSPSemanticTokensFileViewProvider;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Utility class for working with document symbols.
 */
final class LSPDocumentSymbolUtils {

    private static final Key<CachedModel> STRUCTURE_VIEW_MODEL_KEY = Key.create("LSP.DocumentSymbol.StructureViewModel");

    private record CachedModel(long stamp, @NotNull LSPDocumentSymbolStructureViewModel model) {}

    private LSPDocumentSymbolUtils() {
        // Pure utility class
    }

    /**
     * Returns the structure view model for the provided element.
     * The model is cached per-file and invalidated when the document modification stamp changes,
     * so multiple callbacks from the breadcrumbs/sticky-lines provider within a single pass reuse
     * the same instance instead of rebuilding it for every element.
     *
     * @param element the element
     * @return the structure view model for the element, or null if none was found
     */
    @Nullable
    static LSPDocumentSymbolStructureViewModel getStructureViewModel(@NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) return null;
        Editor editor = LSPIJUtils.editorForElement(element);
        if (editor == null) return null;

        if (!Registry.is("lsp4ij.performance.optimizations.enabled")) {
            return new LSPDocumentSymbolStructureViewModel(file, editor);
        }

        Document document = LSPIJUtils.getDocument(file);
        if (document == null) return null;

        long stamp = document.getModificationStamp();
        CachedModel cached = file.getUserData(STRUCTURE_VIEW_MODEL_KEY);
        if (cached != null && cached.stamp() == stamp) {
            return cached.model();
        }

        LSPDocumentSymbolStructureViewModel model = new LSPDocumentSymbolStructureViewModel(file, editor);
        file.putUserData(STRUCTURE_VIEW_MODEL_KEY, new CachedModel(stamp, model));
        return model;
    }

    /**
     * Returns the closest containing document symbol data for the element.
     *
     * @param element the element
     * @return the closest containing document symbol data, or null if none was found
     */
    @Nullable
    static DocumentSymbolData getDocumentSymbolData(@NotNull PsiElement element) {
        LSPSemanticTokensFileViewProvider semanticTokensFileViewProvider = LSPSemanticTokensFileViewProvider.getInstance(element);
        int effectiveOffset = semanticTokensFileViewProvider != null ? semanticTokensFileViewProvider.getEffectiveOffset(element) : -1;
        int offset = effectiveOffset > -1 ? effectiveOffset : element.getTextOffset();
        return getDocumentSymbolData(element, offset);
    }

    @Nullable
    private static DocumentSymbolData getDocumentSymbolData(@NotNull PsiElement element, int offset) {
        if (element instanceof DocumentSymbolData documentSymbolData) {
            return documentSymbolData;
        }

        LSPDocumentSymbolStructureViewModel structureViewModel = getStructureViewModel(element);
        if (structureViewModel != null) {
            PsiFile file = element.getContainingFile();
            Document document = LSPIJUtils.getDocument(file);
            if (document == null) {
                return null;
            }
            int docLength = document.getTextLength();
            List<DocumentSymbolData> containingDocumentSymbolDatas = getContainingDocumentSymbolDatas(document, docLength, structureViewModel.getRoot(), offset);
            // Breadth-first search, so the last one is the closest one
            return ContainerUtil.getLastItem(containingDocumentSymbolDatas);
        }

        return null;
    }

    @NotNull
    private static List<DocumentSymbolData> getContainingDocumentSymbolDatas(@NotNull Document document,
                                                                             int docLength,
                                                                             @NotNull StructureViewTreeElement root,
                                                                             int offset) {
        Queue<StructureViewTreeElement> pending = new ArrayDeque<>(256);
        pending.add(root);
        List<DocumentSymbolData> containingDocumentSymbolDatas = new LinkedList<>();

        while (!pending.isEmpty()) {
            var structureViewTreeElement = pending.remove();
            // If this is file-level, collect its children/descendants
            if (structureViewTreeElement instanceof LSPFileStructureViewElement fileStructureViewElement) {
                pending.addAll(fileStructureViewElement.getChildrenBase());
            }

            // Otherwise add document symbol datas that contain the offset in a breadth-first manner so that the last one is
            // the closest one for the offset
            else if (structureViewTreeElement instanceof LSPDocumentSymbolViewElement documentSymbolViewElement) {
                if (Registry.is("lsp4ij.performance.optimizations.enabled")) {
                    // getTextRangeDirect() avoids both the PSI-anchor read lock and repeated LSP→IJ range conversion
                    TextRange textRange = documentSymbolViewElement.getTextRangeDirect(document, docLength);
                    if ((textRange != null) && textRange.containsOffset(offset)) {
                        // Add this one — only acquire the read lock for matching nodes (typically 1–3 per file)
                        DocumentSymbolData documentSymbolData = documentSymbolViewElement.getElement();
                        if (documentSymbolData != null) {
                            containingDocumentSymbolDatas.add(documentSymbolData);
                        }

                        // And all children/descendants that also contain the offset
                        pending.addAll(documentSymbolViewElement.getChildrenBase());
                    }
                } else {
                    DocumentSymbolData documentSymbolData = documentSymbolViewElement.getElement();
                    DocumentSymbol documentSymbol = documentSymbolData != null ? documentSymbolData.getDocumentSymbol() : null;
                    Range documentSymbolRange = documentSymbol != null ? documentSymbol.getRange() : null;
                    TextRange textRange = documentSymbolRange != null ? LSPIJUtils.toTextRange(documentSymbolRange, document, docLength) : null;
                    if ((textRange != null) && textRange.containsOffset(offset)) {
                        containingDocumentSymbolDatas.add(documentSymbolData);

                        // And all children/descendants that also contain the offset
                        pending.addAll(documentSymbolViewElement.getChildrenBase());
                    }
                }
            }
        }

        return containingDocumentSymbolDatas;
    }
}
