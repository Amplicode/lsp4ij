/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.usages;

import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.features.LSPPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP usage Psi element.
 */
public class LSPUsagePsiElement extends LSPPsiElement {

    private UsageKind kind;

    public static enum UsageKind {
        declarations,
        definitions,
        typeDefinitions,
        references,
        implementations;
    }

    public LSPUsagePsiElement(@NotNull PsiFile file, @NotNull TextRange textRange) {
        super(file, textRange);
        //OPEN IDE BEGIN
        // The Find Usages preview highlights file.findElementAt(getTextOffset()) for named elements, which is the
        // whole file for structureless (TextMate) files; ask it to highlight the usage range itself instead
        Key<Boolean> doNotAdjustNameRangeKey = DoNotAdjustNameRangeKeyHolder.KEY;
        if (doNotAdjustNameRangeKey != null) {
            putUserData(doNotAdjustNameRangeKey, true);
        }
        //OPEN IDE END
    }

    //OPEN IDE BEGIN
    /**
     * Holds {@code UsagePreviewPanel.DO_NOT_ADJUST_NAME_RANGE}, which is looked up by name because it isn't available
     * in all supported IDE versions.
     */
    private static final class DoNotAdjustNameRangeKeyHolder {
        private static final @Nullable Key<Boolean> KEY = findKey();

        @SuppressWarnings({"unchecked", "deprecation"})
        private static @Nullable Key<Boolean> findKey() {
            try {
                // The key is created when UsagePreviewPanel is initialized
                Class.forName("com.intellij.usages.impl.UsagePreviewPanel");
            } catch (ClassNotFoundException | LinkageError e) {
                return null;
            }
            return (Key<Boolean>) Key.findKeyByName("UsageViewPanel.DO_NOT_ADJUST_NAME_RANGE");
        }
    }
    //OPEN IDE END

    /**
     * Returns the usage kind (references, implementations, etc)
     *
     * @return the usage kind (references, implementations, etc)
     */
    public UsageKind getKind() {
        return kind;
    }

    public void setKind(UsageKind kind) {
        this.kind = kind;
    }

    @Override
    public @NotNull Language getLanguage() {
        return getContainingFile().getLanguage();
    }
}
