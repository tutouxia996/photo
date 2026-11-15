# SpektraLab film-edge port

Files under `src/utils/spektra/` contain geometry tables, edge-look data and a
canvas painter **ported from** [JamesQiu2005/SpektraLab](https://github.com/JamesQiu2005/SpektraLab):

- `engine/src/pipeline/overscan.cpp` — formats, KS perforations, Fuji stripes
- `app/SpektraLab/Sources/FilmEdge/FilmEdgeStock.swift` — EdgeLook / EdgeWords
- `app/SpektraLab/Sources/FilmEdge/FilmLayout.swift` — strip layout
- `app/SpektraLab/Sources/FilmEdge/FilmCanvasView.swift` — FilmPainter colours / developed draw order

SpektraLab is licensed under **GPL-3.0-or-later**. These ported modules inherit
that license. This is a web preview composite (not the Metal spectral engine).
