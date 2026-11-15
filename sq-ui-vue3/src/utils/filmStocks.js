/** SpektraLab 风格片边预设（视觉近似，非光谱引擎） */

export const FILM_STOCKS = [
  {
    id: 'kodak_portra_400',
    label: 'Portra 400',
    edgeText: 'KODAK PORTRA 400',
    format: '135',
    ink: '#e8a045',
    inkDim: 'rgba(232, 160, 69, 0.78)',
    base: '#0b0a09',
    baseMid: '#15120f',
    hole: '#f3eee6',
    dateGlow: '#ffb24a',
    dx: true,
    stripes: false,
    sprockets: true,
    face: 'helvetica'
  },
  {
    id: 'fuji_pro_400h',
    label: 'Pro 400H',
    edgeText: 'FUJI 400H',
    format: '135',
    ink: '#f0b25a',
    inkDim: 'rgba(240, 178, 90, 0.8)',
    base: '#0c0b0a',
    baseMid: '#161311',
    hole: '#f5f0e8',
    dateGlow: '#ffc15c',
    dx: true,
    stripes: true,
    sprockets: true,
    face: 'dot'
  },
  {
    id: 'kodak_vision3_500t',
    label: 'Vision3 500T',
    edgeText: 'EASTMAN 5219',
    format: '135',
    ink: '#d9a056',
    inkDim: 'rgba(217, 160, 86, 0.75)',
    base: '#090908',
    baseMid: '#12100e',
    hole: '#efeae2',
    dateGlow: '#f0a84a',
    dx: false,
    stripes: false,
    sprockets: true,
    face: 'cine'
  },
  {
    id: 'ilford_hp5',
    label: 'HP5 Plus',
    edgeText: 'ILFORD HP5 PLUS',
    format: '135',
    ink: '#d8c9a8',
    inkDim: 'rgba(216, 201, 168, 0.78)',
    base: '#0a0a0a',
    baseMid: '#141414',
    hole: '#f2f0ea',
    dateGlow: '#e8d7b0',
    dx: true,
    stripes: false,
    sprockets: true,
    face: 'ilford'
  },
  {
    id: 'fuji_provia_120',
    label: 'Provia 6×9',
    edgeText: 'FUJI RDP III',
    format: '120',
    ink: '#e6b15c',
    inkDim: 'rgba(230, 177, 92, 0.8)',
    base: '#0c0b0a',
    baseMid: '#15120f',
    hole: '#f4efe6',
    dateGlow: '#ffc15c',
    dx: false,
    stripes: false,
    sprockets: false,
    face: 'dot'
  },
  {
    id: 'kodak_portra_645',
    label: 'Portra 645',
    edgeText: 'KODAK PORTRA 400',
    format: '120',
    ink: '#e8a045',
    inkDim: 'rgba(232, 160, 69, 0.78)',
    base: '#0b0a09',
    baseMid: '#15120f',
    hole: '#f3eee6',
    dateGlow: '#ffb24a',
    dx: false,
    stripes: false,
    sprockets: false,
    face: 'helvetica'
  }
]

export function getFilmStock(id) {
  return FILM_STOCKS.find(s => s.id === id) || FILM_STOCKS[0]
}
