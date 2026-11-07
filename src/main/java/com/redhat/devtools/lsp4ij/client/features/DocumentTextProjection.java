/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.features;

import org.jetbrains.annotations.NotNull;

/**
 * The text a language server is shown for a document instead of the document's own: the part written
 * in the server's language, with everything else blanked out.
 * <p>
 * It lets a server of one language serve the code another file type embeds — the JavaScript of an
 * HTML {@code <script>}, say — without a position mapping: a projection must keep the length of the
 * text and every line break where it is, so that each offset, line and column the server answers with
 * is valid in the document as it is.
 * <p>
 * A projected document is synchronized with the full text on every change, whatever the server
 * supports, since an incremental edit of the document is not an edit of its projection.
 */
@FunctionalInterface
public interface DocumentTextProjection {

    /**
     * Returns the projection of the given document text.
     *
     * @param text the document text.
     * @return the projected text, of the same length and with the same line breaks.
     */
    @NotNull
    String project(@NotNull CharSequence text);
}
