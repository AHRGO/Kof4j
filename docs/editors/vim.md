[English](vim.md) | [Português](vim.pt_BR.md)

# Vim

Integration: filetype + syntax + compiler, idiomatic to Vim. Semantics via
LSP (with an LSP client plugin, e.g.: `vim-lsp`).

## Automatic installation

```bash
kof editor install vim
```

Writes:

- `~/.vim/ftdetect/kof.vim` — `*.kf`/`*.kof` → filetype `kof`.
- `~/.vim/after/syntax/kof.vim` — highlight by keywords/types.
- `~/.vim/after/ftplugin/kof.vim` — `commentstring`, `expandtab`, `shiftwidth`.
- `~/.vim/after/compiler/kof.vim` — `:make` delegates to `kof build`;
  `errorformat` matches `file:line:column: msg` (the Kof diagnostics).

## Usage

```vim
:setfiletype kof
:make            " kof build
```

For LSP, register `kof lsp` in your client (e.g.: vim-lsp):

```vim
if executable('kof')
  au User lsp_setup call lsp#register_server({
    \ 'name': 'kof',
    \ 'cmd': {server_info->['kof','lsp']},
    \ 'allowlist': ['kof'],
    \ })
endif
```

## Troubleshooting

- No highlight: `:syntax list kofKeyword` should list the patterns.
