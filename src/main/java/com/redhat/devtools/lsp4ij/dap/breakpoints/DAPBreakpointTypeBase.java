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
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.dap.DAPDebuggerEditorsProvider;
import com.redhat.devtools.lsp4ij.dap.DebugAdapterManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for Debug Adapter Protocol (DAP) breakpoint type.
 *
 * @param <P> the breakpoint point properties.
 */
public abstract class DAPBreakpointTypeBase<P extends XBreakpointProperties<?>> extends XLineBreakpointType<P> {

    protected DAPBreakpointTypeBase(@NonNls @NotNull String id, @Nls @NotNull String title) {
        super(id, title);
    }

    @Override
    public @Nullable XDebuggerEditorsProvider getEditorsProvider(@NotNull XLineBreakpoint<P> breakpoint,
                                                                 @NotNull Project project) {
        // Returns a non-null XDebuggerEditorsProvider to support Condition in the breakpoint type.
        FileType fileType = null;
        var file = LSPIJUtils.findResourceFor(breakpoint.getFileUrl());
        if (file != null) {
            if (!supportsConditionBreakpoint(file, project)) {
                return null;
            }
            fileType = file.getFileType();
        }
        return new DAPDebuggerEditorsProvider(fileType, null);
    }

    /**
     * Use the short (file name + line) description for the breakpoint balloon header instead of the
     * full path-and-line text.
     * <p>
     * The lightweight breakpoint popup ({@code XLightBreakpointPropertiesPanel}) puts this text in a
     * non-shrinking bold {@code JBLabel}. When the file path is long, that label's minimum width
     * exceeds the popup width, so the {@code GridLayoutManager} pushes the right-hand controls
     * ("Restore" link, "Done" button) outside the balloon body and they get clipped. Returning the
     * short text keeps the header's minimum width small so the balloon always lays out within its
     * bounds (the full path is still shown in the Breakpoints dialog).
     */
    @Override
    public @NotNull @Nls String getGeneralDescription(@NotNull XLineBreakpoint<P> breakpoint) {
        return getShortText(breakpoint);
    }

    @Override
    public boolean canPutAt(@NotNull VirtualFile file,
                            int line,
                            @NotNull Project project) {
        return DebugAdapterManager.getInstance().isDebuggableFile(file, this, project);
    }

    protected boolean supportsConditionBreakpoint(VirtualFile file, @NotNull Project project) {
        // TODO: manage a settings to know if the given file which is associated to a DAP server
        // can support condition breakpoint point
        // At this step, the DAP server cannot be started, so we need to manage a DAP server settings for that.
        return true;
    }
}
