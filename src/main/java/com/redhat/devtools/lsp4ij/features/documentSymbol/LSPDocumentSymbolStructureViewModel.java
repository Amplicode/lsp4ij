/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.indexing.ProjectIndexingManager;
import com.redhat.devtools.lsp4ij.features.documentSymbol.filter.*;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Stream;

import static com.redhat.devtools.lsp4ij.features.documentSymbol.LSPDocumentSymbolStructureViewFactory.isSymbolsSupportedByLanguageServer;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.isDoneNormally;
import static com.redhat.devtools.lsp4ij.internal.CompletableFutures.waitUntilDone;

/**
 * LSP document symbol structure view model.
 */
public class LSPDocumentSymbolStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {

    public LSPDocumentSymbolStructureViewModel(@NotNull PsiFile psiFile, @Nullable Editor editor) {
        super(psiFile, editor, new LSPFileStructureViewElement(psiFile));
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
        return element.getChildren().length > 0;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement element) {
        return element.getChildren().length == 0;
    }

    @Override
    protected Class @NotNull [] getSuitableClasses() {
        // Any PSI element
        return new Class[]{PsiElement.class};
    }

    @Override
    public Filter @NotNull [] getFilters() {
        return new Filter[]{
                new HideArraysFilter(),
                new HideBooleansFilter(),
                new HideClassesFilter(),
                new HideConstantsFilter(),
                new HideConstructorsFilter(),
                new HideEnumMembersFilter(),
                new HideEnumsFilter(),
                new HideEventsFilter(),
                new HideFieldsFilter(),
                new HideFilesFilter(),
                new HideFunctionsFilter(),
                new HideInterfacesFilter(),
                new HideKeysFilter(),
                new HideMethodsFilter(),
                new HideModulesFilter(),
                new HideNamespacesFilter(),
                new HideNullsFilter(),
                new HideNumbersFilter(),
                new HideObjectsFilter(),
                new HideOperatorsFilter(),
                new HidePackagesFilter(),
                new HidePropertiesFilter(),
                new HideStringsFilter(),
                new HideStructsFilter(),
                new HideTypeParametersFilter(),
                new HideVariablesFilter()
        };
    }

    @Override
    public void dispose() {
        super.dispose();
        // Do not clear the document symbol cache when the view is disposed.
        // This method is called when switching to another editor.
        // Clearing the cache at this point would cause the document symbols to be reloaded every time the editor is reopened, which is not performance-efficient.
        // The document symbol cache should only be invalidated when the file content changes, not when switching between editors.
        // LSPDocumentSymbolSupport documentSymbolSupport = LSPFileSupport.getSupport(psiFile).getDocumentSymbolSupport();
        // documentSymbolSupport.cancel();
    }

    static class LSPFileStructureViewElement extends PsiTreeElementBase<PsiFile> {

        @Nullable
        private volatile Collection<StructureViewTreeElement> cachedChildren;

        public LSPFileStructureViewElement(@NotNull PsiFile psiFile) {
            super(psiFile);
        }

        @Override
        public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
            if (Registry.is("lsp4ij.performance.optimizations.enabled")) {
                Collection<StructureViewTreeElement> cached = cachedChildren;
                if (cached != null) return cached;
            }
            PsiFile file = getElement();
            return file != null ? collectElements(file) : Collections.emptyList();
        }

        private @NotNull Collection<StructureViewTreeElement> collectElements(@NotNull PsiFile psiFile) {
            if (ProjectIndexingManager.isIndexingAll()) {
                return Collections.emptyList();
            }

            if (!LSPFileSupport.hasSupport(psiFile) || !isSymbolsSupportedByLanguageServer(psiFile)) {
                // Don't force file support creation
                // Ex:
                // 1. when file is closed, document symbol must return an empty list.
                // 2. when language servers are stopped, document symbol must return an empty list.
                return Collections.emptyList();
            }


            LSPFileSupport fileSupport = LSPFileSupport.getSupport(psiFile);
            LSPDocumentSymbolSupport documentSymbolSupport = fileSupport.getDocumentSymbolSupport();
            var params = new DocumentSymbolParams(new TextDocumentIdentifier());
            var documentSymbolFuture = documentSymbolSupport.getDocumentSymbols(params);
            try {
                waitUntilDone(documentSymbolFuture, psiFile);
            } catch (ProcessCanceledException e) {
                throw e;
            } catch (Exception e) {
                return Collections.emptyList();
            }

            if (isDoneNormally(documentSymbolFuture)) {
                var documentSymbols = documentSymbolFuture.getNow(null);
                if (documentSymbols == null) {
                    if (Registry.is("lsp4ij.performance.optimizations.enabled")) {
                        cachedChildren = Collections.emptyList();
                    }
                    return Collections.emptyList();
                }
                Collection<StructureViewTreeElement> result = documentSymbols.stream()
                        .map(LSPDocumentSymbolStructureViewModel::getStructureViewTreeElement)
                        .filter(Objects::nonNull)
                        .toList();
                if (Registry.is("lsp4ij.performance.optimizations.enabled")) {
                    cachedChildren = result;
                }
                return result;
            }
            // Future not yet done — don't cache so the next call retries
            return Collections.emptyList();
        }

        @Override
        public @Nullable String getPresentableText() {
            PsiFile file = getElement();
            return file != null ? file.getName() : null;
        }

        @Override
        public @Nullable String getLocationString() {
            PsiFile file = getElement();
            return file != null ? file.getVirtualFile().getCanonicalPath() : null;
        }

        @Override
        public @Nullable Icon getIcon(boolean unused) {
            PsiFile file = getElement();
            return file != null ? file.getFileType().getIcon() : null;
        }
    }

    public static class LSPDocumentSymbolViewElement extends PsiTreeElementBase<DocumentSymbolData> {

        @Nullable
        private volatile Collection<StructureViewTreeElement> cachedChildren;

        // Stored directly to avoid PSI-anchor read lock during BFS traversal in getContainingDocumentSymbolDatas
        private final @NotNull DocumentSymbol documentSymbolDirect;

        // Cached on first BFS pass; safe because the model itself is invalidated on document modification stamp change
        @Nullable
        private volatile TextRange cachedTextRange;
        private volatile boolean textRangeCached = false;

        public LSPDocumentSymbolViewElement(DocumentSymbolData documentSymbolData) {
            super(documentSymbolData);
            this.documentSymbolDirect = documentSymbolData.getDocumentSymbol();
        }

        /**
         * Returns the underlying LSP {@link DocumentSymbol} without acquiring a read lock.
         * Use this instead of {@code getElement().getDocumentSymbol()} in performance-sensitive paths.
         */
        public @NotNull DocumentSymbol getDocumentSymbolDirect() {
            return documentSymbolDirect;
        }

        /**
         * Returns the {@link TextRange} for this symbol, cached after the first computation.
         * Safe to cache because the parent model is invalidated whenever the document modification stamp changes.
         */
        public @Nullable TextRange getTextRangeDirect(@NotNull Document document, int docLength) {
            if (textRangeCached) return cachedTextRange;
            Range range = documentSymbolDirect.getRange();
            TextRange result = range != null ? LSPIJUtils.toTextRange(range, document, docLength) : null;
            cachedTextRange = result;
            textRangeCached = true;
            return result;
        }

        @Override
        public @NotNull Collection<StructureViewTreeElement> getChildrenBase() {
            if (Registry.is("lsp4ij.performance.optimizations.enabled")) {
                Collection<StructureViewTreeElement> cached = cachedChildren;
                if (cached != null) return cached;
                Collection<StructureViewTreeElement> result = collectElements(getElement());
                cachedChildren = result;
                return result;
            }
            return collectElements(getElement());
        }

        private @NotNull Collection<StructureViewTreeElement> collectElements(@Nullable DocumentSymbolData documentSymbolData) {
            var children = documentSymbolData != null ? documentSymbolData.getChildren() : null;
            if (ArrayUtil.isEmpty(children)) {
                return Collections.emptyList();
            }
            return Stream.of(children)
                    .map(LSPDocumentSymbolStructureViewModel::getStructureViewTreeElement)
                    .filter(Objects::nonNull)
                    .toList();
        }

        @Override
        public @Nullable String getPresentableText() {
            DocumentSymbolData documentSymbolData = getElement();
            return documentSymbolData != null ? documentSymbolData.getPresentableText() : null;
        }
    }

    private static @Nullable StructureViewTreeElement getStructureViewTreeElement(DocumentSymbolData documentSymbol) {
        return documentSymbol.getClientFeatures().getDocumentSymbolFeature().getStructureViewTreeElement(documentSymbol);
    }
}