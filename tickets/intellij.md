
# Worktree Slots

![other ticket](./worktre-slots.md)



# go to and find all refs

Intellij indexing MCP server? http://plugins.jetbrains.com/plugin/29174-ide-index-mcp-server

...


Consider filtering these.

DONE - below

0 = {

DefaultToolDefinition@16889} "

DefaultToolDefinition[name=execute_run_configuration, description=Run a specific run
configuration in the current project and wait up to specified timeout for it to finish.\nUse this tool to run a run
configuration that you have found from the "get_run_configurations" tool.\nReturns the execution result including exit
code, output, and success status., inputSchema={"type":"object","properties":{"configurationName":{"type":"string","
description":"Name of the run configuration to execute"},"timeout":{"type":"integer","description":"Timeout in
milliseconds"},"maxLinesCount":{"type":"integer","description":"Maximum number of lines to return"},"truncateMode":{"
enum":["START","MIDDLE","END","NONE"],"description":"How to truncate the text: from the start, in the middle, at the
end, or don't truncate at all"},"projectPath":{"type":"string","description":" The project path. Pass this value ALWAYS
if you are aware of it. It reduces numbers of ambiguous calls. \n In the case you know only the curre"
1 = {

DefaultToolDefinition@16890} "

DefaultToolDefinition[name=get_run_configurations, description=Returns a list of run
configurations for the current project.\nRun configurations are usually used to define user the way how to run a user
application, task or test suite from sources.\n\nThis tool provides additional info like command line, working
directory, and environment variables if they are available.\n\nUse this tool to query the list of available run
configurations in the current project., inputSchema={"type":"object","properties":{"projectPath":{"type":"string","
description":" The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
\n In the case you know only the current working directory you can use it as the project path.\n If you're not aware
about the project path you can ask user about it."}},"required":[]}]"
2 = {

DefaultToolDefinition@16891} "

DefaultToolDefinition[name=build_project, description=Triggers building of the
project or specified files, waits for completion, and returns build errors.\nUse this tool to build the project or
compile files and get detailed information about compilation errors and warnings.\nYou have to use this tool after
performing edits to validate if the edits are valid., inputSchema={"type":"object","properties":{"rebuild":{"type":"
boolean","description":"Whether to perform full rebuild the project. Defaults to false. Effective only when
`filesToRebuild` is not specified."},"filesToRebuild":{"type":"array","items":{"type":"string"},"description":"If
specified, only compile files with the specified paths. Paths are relative to the project root."},"timeout":{"type":"
integer","description":"Timeout in milliseconds"},"projectPath":{"type":"string","description":" The project path. Pass
this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls. \n In the case you know only the curr"
3 = {

DefaultToolDefinition@16892} "

DefaultToolDefinition[name=get_file_problems, description=Analyzes the specified file
for errors and warnings using IntelliJ's inspections.\nUse this tool to identify coding issues, syntax errors, and other
problems in a specific file.\nReturns a list of problems found in the file, including severity, description, and
location information.\nNote: Only analyzes files within the project directory.\nNote: Lines and Columns are 1-based.,
inputSchema={"type":"object","properties":{"filePath":{"type":"string","description":"Path relative to the project
root"},"errorsOnly":{"type":"boolean","description":"Whether to include only errors or include both errors and
warnings"},"timeout":{"type":"integer","description":"Timeout in milliseconds"},"projectPath":{"type":"string","
description":" The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
\n In the case you know only the current working directory you can use it as the project path.\n If you're no"
4 = {

DefaultToolDefinition@16893} "

DefaultToolDefinition[name=get_project_dependencies, description=Get a list of all
dependencies defined in the project.\nReturns structured information about project library names., inputSchema={"type":"
object","properties":{"projectPath":{"type":"string","description":" The project path. Pass this value ALWAYS if you are
aware of it. It reduces numbers of ambiguous calls. \n In the case you know only the current working directory you can
use it as the project path.\n If you're not aware about the project path you can ask user about it."}},"required":[]}]"
5 = {

DefaultToolDefinition@16894} "

DefaultToolDefinition[name=get_project_modules, description=Get a list of all modules
in the project with their types.\nReturns structured information about each module including name and type.,
inputSchema={"type":"object","properties":{"projectPath":{"type":"string","description":" The project path. Pass this
value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls. \n In the case you know only the current
working directory you can use it as the project path.\n If you're not aware about the project path you can ask user
about it."}},"required":[]}]"
6 = {

DefaultToolDefinition@16895} "

DefaultToolDefinition[name=create_new_file, description=Creates a new file at the
specified path within the project directory and optionally populates it with text if provided.\nUse this tool to
generate new files in your project structure.\nNote: Creates any necessary parent directories automatically,
inputSchema={"type":"object","properties":{"pathInProject":{"type":"string","description":"Path where the file should be
created relative to the project root"},"text":{"type":"string","description":"Content to write into the new file"},"
overwrite":{"type":"boolean","description":"Whether to overwrite an existing file if exists. If false, an exception is
thrown in case of a conflict."},"projectPath":{"type":"string","description":" The project path. Pass this value ALWAYS
if you are aware of it. It reduces numbers of ambiguous calls. \n In the case you know only the current working
directory you can use it as the project path.\n If you're not aware about the project path you can ask user ab"
7 = {

DefaultToolDefinition@16896} "

DefaultToolDefinition[name=find_files_by_glob, description=Searches for all files in
the project whose relative paths match the specified glob pattern.\nThe search is performed recursively in all
subdirectories of the project directory or a specified subdirectory.\nUse this tool when you need to find files by a
glob pattern (e.g. '**/*.txt')., inputSchema={"type":"object","properties":{"globPattern":{"type":"string","
description":"Glob pattern to search for. The pattern must be relative to the project root. Example:
`src/**/ *.java`"},"subDirectoryRelativePath":{"type":"string","description":"Optional subdirectory relative to the
project to search in."},"addExcluded":{"type":"boolean","description":"Whether to add excluded/ignored files to the
search results. Files can be excluded from a project either by user of by some ignore rules"},"fileCountLimit":{"type":"
integer","description":"Maximum number of files to return."},"timeout":{"type":"integer","description":"Timeout in
milliseconds""
8 = {

DefaultToolDefinition@16897} "

DefaultToolDefinition[name=find_files_by_name_keyword, description=Searches for all
files in the project whose names contain the specified keyword (case-insensitive).\nUse this tool to locate files when
you know part of the filename.\nNote: Matched only names, not paths, because works via indexes.\nNote: Only searches
through files within the project directory, excluding libraries and external dependencies.\nNote: Prefer this tool over
other `find` tools because it's much faster, \nbut remember that this tool searches only names, not paths and it doesn't
support glob patterns., inputSchema={"type":"object","properties":{"nameKeyword":{"type":"string","description":"
Substring to search for in file names"},"fileCountLimit":{"type":"integer","description":"Maximum number of files to
return."},"timeout":{"type":"integer","description":"Timeout in milliseconds"},"projectPath":{"type":"string","
description":" The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of a"
9 = {

DefaultToolDefinition@16898} "

DefaultToolDefinition[name=get_all_open_file_paths, description=Returns active
editor's and other open editors' file paths relative to the project root.\n\nUse this tool to explore current open
editors., inputSchema={"type":"object","properties":{"projectPath":{"type":"string","description":" The project path.
Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls. \n In the case you know only the
current working directory you can use it as the project path.\n If you're not aware about the project path you can ask
user about it."}},"required":[]}]"
10 = {

DefaultToolDefinition@16899} "

DefaultToolDefinition[name=list_directory_tree, description=Provides a tree
representation of the specified directory in the pseudo graphic format like `tree` utility does.\nUse this tool to
explore the contents of a directory or the whole project.\nYou MUST prefer this tool over listing directories via
command line utilities like `ls` or `dir`., inputSchema={"type":"object","properties":{"directoryPath":{"type":"
string","description":"Path relative to the project root"},"maxDepth":{"type":"integer","description":"Maximum recursion
depth"},"timeout":{"type":"integer","description":"Timeout in milliseconds"},"projectPath":{"type":"string","
description":" The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
\n In the case you know only the current working directory you can use it as the project path.\n If you're not aware
about the project path you can ask user about it."}},"required":["directoryPath"]}]"
11 = {

DefaultToolDefinition@16900} "

DefaultToolDefinition[name=open_file_in_editor, description=Opens the specified file
in the JetBrains IDE editor.\nRequires a filePath parameter containing the path to the file to open.\nThe file path can
be absolute or relative to the project root., inputSchema={"type":"object","properties":{"filePath":{"type":"string","
description":"Path relative to the project root"},"projectPath":{"type":"string","description":" The project path. Pass
this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls. \n In the case you know only the
current working directory you can use it as the project path.\n If you're not aware about the project path you can ask
user about it."}},"required":["filePath"]}]"
12 = {

DefaultToolDefinition@16901} "

DefaultToolDefinition[name=reformat_file, description=Reformats a specified file in
the JetBrains IDE.\nUse this tool to apply code formatting rules to a file identified by its path., inputSchema={"
type":"object","properties":{"path":{"type":"string","description":"Path relative to the project root"},"projectPath":{"
type":"string","description":" The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of
ambiguous calls. \n In the case you know only the current working directory you can use it as the project path.\n If
you're not aware about the project path you can ask user about it."}},"required":["path"]}]"
13 = {

DefaultToolDefinition@16902} "

DefaultToolDefinition[name=get_file_text_by_path, description= Retrieves the text
content of a file using its path relative to project root.\n Use this tool to read file contents when you have the
file's project-relative path.\n In the case of binary files, the tool returns an error.\n If the file is too large, the
text will be truncated with '<<<...content truncated...>>>' marker and in according to the `truncateMode` parameter.,
inputSchema={"type":"object","properties":{"pathInProject":{"type":"string","description":"Path relative to the project
root"},"truncateMode":{"enum":["START","MIDDLE","END","NONE"],"description":"How to truncate the text: from the start,
in the middle, at the end, or don't truncate at all"},"maxLinesCount":{"type":"integer","description":"Max number of
lines to return. Truncation will be performed depending on truncateMode."},"projectPath":{"type":"string","
description":" The project path. Pass this value ALWAYS if you are aware of i"
14 = {

DefaultToolDefinition@16903} "

DefaultToolDefinition[name=replace_text_in_file, description= Replaces text in a
file with flexible options for find and replace operations.\n Use this tool to make targeted changes without replacing
the entire file content.\n This is the most efficient tool for file modifications when you know the exact text to
replace.\n \n Requires three parameters:\n - pathInProject: The path to the target file, relative to project root\n -
oldTextOrPatte: The text to be replaced (exact match by default)\n - newText: The replacement text\n \n Optional
parameters:\n - replaceAll: Whether to replace all occurrences (default: true)\n - caseSensitive: Whether the search is
case-sensitive (default: true)\n - regex: Whether to treat oldText as a regular expression (default: false)\n \n Returns
one of these responses:\n - "ok" when replacement happened\n - error "project dir not found" if project directo"
15 = {

DefaultToolDefinition@16904} "

DefaultToolDefinition[name=search_in_files_by_regex, description=Searches with a
regex pattern within all files in the project using IntelliJ's search engine.\nPrefer this tool over reading files with
command-line tools because it's much faster.\n\nThe result occurrences are surrounded with || characters, e.g.
`some text ||substring|| text`, inputSchema={"type":"object","properties":{"regexPattern":{"type":"string","
description":"Regex patter to search for"},"directoryToSearch":{"type":"string","description":"Directory to search in,
relative to project root. If not specified, searches in the entire project."},"fileMask":{"type":"string","
description":"File mask to search for. If not specified, searches for all files. Example: `*.java`"},"caseSensitive":{"
type":"boolean","description":"Whether to search for the text in a case-sensitive manner"},"maxUsageCount":{"type":"
integer","description":"Maximum number of entries to return."},"timeout":{"type":"integer","description":"Timeout in
milli"
16 = {

DefaultToolDefinition@16905} "

DefaultToolDefinition[name=search_in_files_by_text, description=Searches for a text
substring within all files in the project using IntelliJ's search engine.\nPrefer this tool over reading files with
command-line tools because it's much faster.\n\nThe result occurrences are surrounded with `||` characters, e.g.
`some text ||substring|| text`, inputSchema={"type":"object","properties":{"searchText":{"type":"string","description":"
Text substring to search for"},"directoryToSearch":{"type":"string","description":"Directory to search in, relative to
project root. If not specified, searches in the entire project."},"fileMask":{"type":"string","description":"File mask
to search for. If not specified, searches for all files. Example: `*.java`"},"caseSensitive":{"type":"boolean","
description":"Whether to search for the text in a case-sensitive manner"},"maxUsageCount":{"type":"integer","
description":"Maximum number of entries to return."},"timeout":{"type":"integer","description":"Timeout in mill"
17 = {

DefaultToolDefinition@16906} "

DefaultToolDefinition[name=get_symbol_info, description=Retrieves information about
the symbol at the specified position in the specified file.\nProvides the same information as Quick Documentation
feature of IntelliJ IDEA does.\n\nThis tool is useful for getting information about the symbol at the specified position
in the specified file.\nThe information may include the symbol's name, signature, type, documentation, etc. It depends
on a particular language.\n\nIf the position has a reference to a symbol the tool will return a piece of code with the
declaration of the symbol if possible.\n\nUse this tool to understand symbols declaration, semantics, where it's
declared, etc., inputSchema={"type":"object","properties":{"filePath":{"type":"string","description":"Path relative to
the project root"},"line":{"type":"integer","description":"1-based line number"},"column":{"type":"integer","
description":"1-based column number"},"projectPath":{"type":"string","description":" The project path. Pass th"
18 = {

DefaultToolDefinition@16907} "

DefaultToolDefinition[name=rename_refactoring, description= Renames a symbol (
variable, function, class, etc.) in the specified file.\n Use this tool to perform rename refactoring operations. \n \n
The `rename_refactoring` tool is a powerful, context-aware utility. Unlike a simple text search-and-replace, \n it
understands the code's structure and will intelligently update ALL references to the specified symbol throughout the
project,\n ensuring code integrity and preventing broken references. It is ALWAYS the preferred method for renaming
programmatic symbols.\n\n Requires three parameters:\n - pathInProject: The relative path to the file from the project's
root directory (e.g., `src/api/controllers/userController.js`)\n - symbolName: The exact, case-sensitive name of the
existing symbol to be renamed (e.g., `getUserData`)\n - newName: The new, case-sensitive name for the symbol (e.g.,
`fetchUserData`).\n          "
19 = {

DefaultToolDefinition@16908} "

DefaultToolDefinition[name=execute_terminal_command, description= Executes a
specified shell command in the IDE's integrated terminal.\n Use this tool to run terminal commands within the IDE
environment.\n Requires a command parameter containing the shell command to execute.\n Important features and
limitations:\n - Checks if process is running before collecting output\n - Limits output to 2000 lines (truncates
excess)\n - Times out after specified timeout with notification\n - Requires user confirmation unless "Brave Mode" is
enabled in settings\n Returns possible responses:\n - Terminal output (truncated if > 2000 lines)\n - Output with
interruption notice if timed out\n - Error messages for various failure cases, inputSchema={"type":"object","
properties":{"command":{"type":"string","description":"Shell command to execute"},"executeInShell":{"type":"boolean","
description":"Whether to execute the command in a def"
20 = {

DefaultToolDefinition@16909} "

DefaultToolDefinition[name=get_repositories, description=Retrieves the list of VCS
roots in the project.\nThis is useful to detect all repositories in a multi-repository project., inputSchema={"type":"
object","properties":{"projectPath":{"type":"string","description":" The project path. Pass this value ALWAYS if you are
aware of it. It reduces numbers of ambiguous calls. \n In the case you know only the current working directory you can
use it as the project path.\n If you're not aware about the project path you can ask user about it."}},"required":[]}]"
21 = {

DefaultToolDefinition@16910} "

DefaultToolDefinition[name=runNotebookCell, description= Execute one or all cells of
a Jupyter notebook.\n Parameters:\n - file_path: absolute path to a .ipynb file.\n - cell_id (optional): Jupyter cell ID
hash (string). If omitted, the entire notebook will be executed.\n Notes:\n - This action runs inside the IDE on the
file specified by file_path.\n - If the file cannot be found, the action returns an error.\n Examples:\n - {"
file_path": "/abs/path/demo.ipynb", "cell_id": "13c5cec416369e19"}\n - {"file_path": "/abs/path/demo.ipynb"},
inputSchema={"type":"object","properties":{"file_path":{"type":"string","description":"Absolute path to the .ipynb
notebook"},"cell_id":{"type":"string","description":"Optional Jupyter cell ID. If omitted, all cells are executed."},"
projectPath":{"type":"string","description":" The project path. Pass this value ALWAYS if you are aware of it. It
reduces numbers of ambiguous calls. \n In the case you know only the c"
22 = {

DefaultToolDefinition@16911} "

DefaultToolDefinition[name=permission_prompt, description=permission_prompt,
inputSchema={"type":"object","properties":{"tool_use_id":{"type":"string"},"tool_name":{"type":"string"},"input":{"
type":"object","additionalProperties":{"type":"object","required":[],"properties":{}}},"projectPath":{"type":"string","
description":" The project path. Pass this value ALWAYS if you are aware of it. It reduces numbers of ambiguous calls.
\n In the case you know only the current working directory you can use it as the project path.\n If you're not aware
about the project path you can ask user about it."}},"required":["tool_use_id","tool_name"]}]"