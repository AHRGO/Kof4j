[English](emacs.md) | [Português](emacs.pt_BR.md)

# Emacs

Integration: `kof-mode` (keyword highlighting) + LSP via `eglot`.
No parser of its own.

## Automatic installation

```bash
kof editor install emacs
```

Writes `~/.emacs.d/lisp/kof-mode.el` — defines `kof-mode` (prog-mode),
syntax table (comments `//` and `/* */`, strings), `font-lock` by
keyword and `auto-mode-alist` for `*.kf`/`*.kof`.

## Configuration

```elisp
(add-to-list 'load-path "~/.emacs.d/lisp")
(require 'kof-mode)
(with-eval-after-load 'eglot
  (add-to-list 'eglot-server-programs '(kof-mode . ("kof" "lsp"))))
```

Opening a `.kof` activates `kof-mode`; `M-x eglot` connects to `kof lsp`
(diagnostics, completion, hover, rename, references).

## Troubleshooting

- `M-: (require 'kof-mode)` should load without error.
- `M-x eglot` in the `.kof` buffer should show a connection to the server.
