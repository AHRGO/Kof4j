[English](neovim.md) | [Português](neovim.pt_BR.md)

# Neovim

Integration: filetype + native LSP (`vim.lsp`) pointing to `kof lsp`.
No parser of its own — semantics come from the official LSP.

## Automatic installation

```bash
kof editor install neovim
```

Writes:

- `~/.config/nvim/ftdetect/kof.lua` — recognizes `*.kf`/`*.kof` as filetype `kof`.
- `~/.config/nvim/after/ftplugin/kof.lua` — basic comment/indent +
  `vim.lsp.start({ cmd = { "kof", "lsp" } })` with `root_dir` resolved by
  `kof.toml`/`.git`.

## Manual installation

```lua
vim.filetype.add({ extension = { kf = 'kof', kof = 'kof' } })
vim.api.nvim_create_autocmd('FileType', {
  pattern = 'kof',
  callback = function()
    vim.lsp.start({
      name = 'kof',
      cmd = { 'kof', 'lsp' },
      root_dir = vim.fs.dirname(vim.fs.find({ 'kof.toml', '.git' },
        { upward = true })[1]) or vim.fn.getcwd(),
    })
  end,
})
```

## Usage

- `gd` go-to-definition, `gr` references, `<leader>rn` rename, `K` hover —
  all via LSP.
- Formatting: `kof fmt <file>` (or `:silent !kof fmt %`).

## Troubleshooting

- `:LspInfo` should show `kof` active. If not, check `which kof`.
