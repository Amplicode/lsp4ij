/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.server.capabilities.ImplementationCapabilityRegistry;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP implementation feature.
 */
@ApiStatus.Experimental
public class LSPImplementationFeature extends AbstractLSPDocumentFeature {

    private ImplementationCapabilityRegistry implementationCapabilityRegistry;

    @Override
    public boolean isSupported(@NotNull PsiFile file) {
        return isImplementationSupported(file);
    }

    /**
     * Returns true if the file associated with a language server can support implementation and false otherwise.
     *
     * @param file the file.
     * @return true if the file associated with a language server can support implementation and false otherwise.
     */
    public boolean isImplementationSupported(@NotNull PsiFile file) {
        return getImplementationCapabilityRegistry().isImplementationSupported(file);
    }

    //OPEN IDE BEGIN
    /**
     * Returns true if the language server supports textDocument/implementation requests for functions,
     * and false otherwise.
     * <p>
     * By default, this method returns true. Language server implementations can override this method
     * to return false if the server does not support or throws errors when requesting implementations
     * for standalone functions (as opposed to methods).
     * <p>
     * For example, the Go language server throws an error when requesting implementations for functions
     * because only methods (not standalone functions) can implement interfaces in Go.
     *
     * @param file the file.
     * @return true if the language server supports implementation requests for functions; false otherwise.
     */
    public boolean isImplementationForFunctionSupported(@NotNull PsiFile file) {
        return true;
    }
    //OPEN IDE END

    public ImplementationCapabilityRegistry getImplementationCapabilityRegistry() {
        if (implementationCapabilityRegistry == null) {
            initImplementationCapabilityRegistry();
        }
        return implementationCapabilityRegistry;
    }

    private synchronized void initImplementationCapabilityRegistry() {
        if (implementationCapabilityRegistry != null) {
            return;
        }
        var clientFeatures = getClientFeatures();
        implementationCapabilityRegistry = new ImplementationCapabilityRegistry(clientFeatures);
        implementationCapabilityRegistry.setServerCapabilities(clientFeatures.getServerWrapper().getServerCapabilitiesSync());
    }

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        if (implementationCapabilityRegistry != null) {
            implementationCapabilityRegistry.setServerCapabilities(serverCapabilities);
        }
    }

}
