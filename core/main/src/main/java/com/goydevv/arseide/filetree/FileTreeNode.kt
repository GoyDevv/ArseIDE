package com.goydevv.arseide.filetree

import com.goydevv.arseide.file.FileObject

data class FileTreeNode(val file: FileObject, val isFile: Boolean, val isDirectory: Boolean, val name: String)
