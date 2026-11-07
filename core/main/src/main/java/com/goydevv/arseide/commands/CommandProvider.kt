package com.goydevv.arseide.commands

import androidx.compose.runtime.mutableStateListOf
import com.goydevv.arseide.commands.editor.CopyCommand
import com.goydevv.arseide.commands.editor.CutCommand
import com.goydevv.arseide.commands.editor.DependenciesCommand
import com.goydevv.arseide.commands.editor.DuplicateLineCommand
import com.goydevv.arseide.commands.editor.EmulateKeyCommand
import com.goydevv.arseide.commands.editor.JumpToLineCommand
import com.goydevv.arseide.commands.editor.LowerCaseCommand
import com.goydevv.arseide.commands.editor.MarkdownPreviewCommand
import com.goydevv.arseide.commands.editor.PasteCommand
import com.goydevv.arseide.commands.editor.RedoCommand
import com.goydevv.arseide.commands.editor.RefreshCommand
import com.goydevv.arseide.commands.editor.ReplaceCommand
import com.goydevv.arseide.commands.editor.RunCommand
import com.goydevv.arseide.commands.editor.SaveCommand
import com.goydevv.arseide.commands.editor.SearchCommand
import com.goydevv.arseide.commands.editor.SelectAllCommand
import com.goydevv.arseide.commands.editor.SelectWordCommand
import com.goydevv.arseide.commands.editor.ShareCommand
import com.goydevv.arseide.commands.editor.SortLinesAscendingCommand
import com.goydevv.arseide.commands.editor.SortLinesDescendingCommand
import com.goydevv.arseide.commands.editor.SyncCommand
import com.goydevv.arseide.commands.editor.SyntaxHighlightingCommand
import com.goydevv.arseide.commands.editor.ToggleExtraKeysCommand
import com.goydevv.arseide.commands.editor.ToggleReadOnlyCommand
import com.goydevv.arseide.commands.editor.ToggleShowAllFilesCommand
import com.goydevv.arseide.commands.editor.ToggleWordWrapCommand
import com.goydevv.arseide.commands.editor.UndoCommand
import com.goydevv.arseide.commands.editor.UpperCaseCommand
import com.goydevv.arseide.commands.global.CommandPaletteCommand
import com.goydevv.arseide.commands.global.DocumentationCommand
import com.goydevv.arseide.commands.global.NewFileCommand
import com.goydevv.arseide.commands.global.SaveAllCommand
import com.goydevv.arseide.commands.global.SearchCodeCommand
import com.goydevv.arseide.commands.global.SearchFileFolderCommand
import com.goydevv.arseide.commands.global.SettingsCommand
import com.goydevv.arseide.commands.global.TerminalCommand
import com.goydevv.arseide.commands.lsp.FormatDocumentCommand
import com.goydevv.arseide.commands.lsp.FormatSelectionCommand
import com.goydevv.arseide.commands.lsp.GoToDefinitionCommand
import com.goydevv.arseide.commands.lsp.GoToReferencesCommand
import com.goydevv.arseide.commands.lsp.RenameSymbolCommand
import com.goydevv.arseide.extension.api.XedExtensionPoint

object CommandProvider {
    private val _commandList = mutableStateListOf<Command>()
    val commandList: List<Command>
        get() = _commandList

    lateinit var DocumentationCommand: DocumentationCommand
    lateinit var TerminalCommand: TerminalCommand
    lateinit var SettingsCommand: SettingsCommand
    lateinit var NewFileCommand: NewFileCommand
    lateinit var CommandPaletteCommand: CommandPaletteCommand
    lateinit var SearchFileFolderCommand: SearchFileFolderCommand
    lateinit var SearchCodeCommand: SearchCodeCommand
    lateinit var CutCommand: CutCommand
    lateinit var CopyCommand: CopyCommand
    lateinit var PasteCommand: PasteCommand
    lateinit var SelectAllCommand: SelectAllCommand
    lateinit var SelectWordCommand: SelectWordCommand
    lateinit var DuplicateLineCommand: DuplicateLineCommand
    lateinit var LowerCaseCommand: LowerCaseCommand
    lateinit var UpperCaseCommand: UpperCaseCommand
    lateinit var SaveCommand: SaveCommand
    lateinit var SaveAllCommand: SaveAllCommand
    lateinit var UndoCommand: UndoCommand
    lateinit var RedoCommand: RedoCommand
    lateinit var RunCommand: RunCommand
    lateinit var SyncCommand: SyncCommand
    lateinit var MarkdownPreviewCommand: MarkdownPreviewCommand
    lateinit var DependenciesCommand: DependenciesCommand
    lateinit var ToggleReadOnlyCommand: ToggleReadOnlyCommand
    lateinit var ToggleShowAllFilesCommand: ToggleShowAllFilesCommand
    lateinit var ToggleExtraKeysCommand: ToggleExtraKeysCommand
    lateinit var SearchCommand: SearchCommand
    lateinit var ReplaceCommand: ReplaceCommand
    lateinit var RefreshCommand: RefreshCommand
    lateinit var SyntaxHighlightingCommand: SyntaxHighlightingCommand
    lateinit var ToggleWordWrapCommand: ToggleWordWrapCommand
    lateinit var JumpToLineCommand: JumpToLineCommand
    lateinit var SortLinesAscendingCommand: SortLinesAscendingCommand
    lateinit var SortLinesDescendingCommand: SortLinesDescendingCommand
    lateinit var ShareCommand: ShareCommand
    lateinit var EmulateKeyCommand: EmulateKeyCommand
    lateinit var GoToDefinitionCommand: GoToDefinitionCommand
    lateinit var GoToReferencesCommand: GoToReferencesCommand
    lateinit var RenameSymbolCommand: RenameSymbolCommand
    lateinit var FormatDocumentCommand: FormatDocumentCommand
    lateinit var FormatSelectionCommand: FormatSelectionCommand

    fun buildCommands() =
        synchronized(this) {
            registerBuiltin(DocumentationCommand()) { DocumentationCommand = it }
            registerBuiltin(TerminalCommand()) { TerminalCommand = it }
            registerBuiltin(SettingsCommand()) { SettingsCommand = it }
            registerBuiltin(NewFileCommand()) { NewFileCommand = it }
            registerBuiltin(CommandPaletteCommand()) { CommandPaletteCommand = it }
            registerBuiltin(SearchFileFolderCommand()) { SearchFileFolderCommand = it }
            registerBuiltin(SearchCodeCommand()) { SearchCodeCommand = it }
            registerBuiltin(CutCommand()) { CutCommand = it }
            registerBuiltin(CopyCommand()) { CopyCommand = it }
            registerBuiltin(PasteCommand()) { PasteCommand = it }
            registerBuiltin(SelectAllCommand()) { SelectAllCommand = it }
            registerBuiltin(SelectWordCommand()) { SelectWordCommand = it }
            registerBuiltin(DuplicateLineCommand()) { DuplicateLineCommand = it }
            registerBuiltin(LowerCaseCommand()) { LowerCaseCommand = it }
            registerBuiltin(UpperCaseCommand()) { UpperCaseCommand = it }
            registerBuiltin(SaveCommand()) { SaveCommand = it }
            registerBuiltin(SaveAllCommand()) { SaveAllCommand = it }
            registerBuiltin(UndoCommand()) { UndoCommand = it }
            registerBuiltin(RedoCommand()) { RedoCommand = it }
            registerBuiltin(RunCommand()) { RunCommand = it }
            registerBuiltin(SyncCommand()) { SyncCommand = it }
            registerBuiltin(MarkdownPreviewCommand()) { MarkdownPreviewCommand = it }
            registerBuiltin(DependenciesCommand()) { DependenciesCommand = it }
            registerBuiltin(ToggleReadOnlyCommand()) { ToggleReadOnlyCommand = it }
            registerBuiltin(ToggleShowAllFilesCommand()) { ToggleShowAllFilesCommand = it }
            registerBuiltin(ToggleExtraKeysCommand()) { ToggleExtraKeysCommand = it }
            registerBuiltin(SearchCommand()) { SearchCommand = it }
            registerBuiltin(ReplaceCommand()) { ReplaceCommand = it }
            registerBuiltin(RefreshCommand()) { RefreshCommand = it }
            registerBuiltin(SyntaxHighlightingCommand()) { SyntaxHighlightingCommand = it }
            registerBuiltin(ToggleWordWrapCommand()) { ToggleWordWrapCommand = it }
            registerBuiltin(JumpToLineCommand()) { JumpToLineCommand = it }
            registerBuiltin(SortLinesAscendingCommand()) { SortLinesAscendingCommand = it }
            registerBuiltin(SortLinesDescendingCommand()) { SortLinesDescendingCommand = it }
            registerBuiltin(ShareCommand()) { ShareCommand = it }
            registerBuiltin(EmulateKeyCommand()) { EmulateKeyCommand = it }
            registerBuiltin(GoToDefinitionCommand()) { GoToDefinitionCommand = it }
            registerBuiltin(GoToReferencesCommand()) { GoToReferencesCommand = it }
            registerBuiltin(RenameSymbolCommand()) { RenameSymbolCommand = it }
            registerBuiltin(FormatDocumentCommand()) { FormatDocumentCommand = it }
            registerBuiltin(FormatSelectionCommand()) { FormatSelectionCommand = it }
        }

    private fun <T : Command> registerBuiltin(command: T, assign: (T) -> Unit) {
        if (_commandList.contains(command)) return
        assign(command)
        _commandList.add(command)
    }

    @XedExtensionPoint
    fun registerCommand(command: Command) {
        val index = _commandList.indexOf(command)
        if (index >= 0) {
            _commandList[index] = command
        } else {
            _commandList.add(command)
        }
    }

    @XedExtensionPoint
    fun unregisterCommand(command: Command) {
        _commandList.remove(command)
    }

    fun getForId(id: String): Command? = findRecursive(id, commandList)

    fun getParentCommand(command: Command): Command? = findParent(command, commandList)

    private fun findParent(target: Command, commands: List<Command>): Command? {
        for (parent in commands) {
            val children = parent.childCommands
            if (children.any { it.id == target.id }) return parent

            val match = findParent(target, children)
            if (match != null) return match
        }
        return null
    }

    private fun findRecursive(id: String, commands: List<Command>): Command? {
        for (command in commands) {
            if (command.id == id) return command
            val children = command.childCommands

            val match = findRecursive(id, children)
            if (match != null) return match
        }
        return null
    }
}
