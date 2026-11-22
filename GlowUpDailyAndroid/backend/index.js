import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import multer from 'multer';

const app = express();
const port = Number(process.env.PORT || 3000);
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 8 * 1024 * 1024 },
  fileFilter: (_req, file, callback) => {
    const allowed = ['image/jpeg', 'image/png', 'image/webp'];
    callback(allowed.includes(file.mimetype) ? null : new Error('Formato no compatible. Usa JPG, PNG o WebP.'), allowed.includes(file.mimetype));
  }
});

app.use(cors({ origin: process.env.CORS_ORIGIN || '*' }));
app.get('/health', (_req, res) => res.json({ ok: true, service: 'Glow Up Daily AI backend' }));

const prompts = {
  face: `Responde en español con orientación general, amable y no crítica. Describe solamente detalles visibles que puedan servir para elegir cuidados básicos de piel o peinados. Puedes comentar iluminación y presentación de la fotografía. No evalúes atractivo, belleza, simetría, edad, raza, origen, identidad, salud mental ni personalidad. No enumeres defectos. No diagnostiques enfermedades, acné, dermatitis ni ninguna condición a partir de la imagen. Si algo parece una posible preocupación médica, indica que una foto no basta y sugiere consultar a un profesional de salud. Da una rutina simple y prudente, evitando productos agresivos. No hagas inferencias sensibles.`,
  food: `Responde en español. Identifica con cautela los alimentos que parecen estar presentes y separa observaciones de suposiciones. Explica que una foto no permite medir porciones, ingredientes, aceite o método de cocción con precisión. Si das valores nutricionales, utiliza intervalos aproximados, explica la incertidumbre y no inventes precisión. No recomiendes pérdida de peso, ayuno, restricción de calorías, saltarse comidas ni compensar comidas. No establezcas metas calóricas. Si no puedes estimar algo, dilo claramente.`
};

app.post('/api/analyze/:kind', upload.single('image'), async (req, res) => {
  try {
    const kind = req.params.kind;
    if (!['face', 'food'].includes(kind)) return res.status(404).json({ error: 'Tipo de análisis no reconocido.' });
    if (!req.file) return res.status(400).json({ error: 'Falta la fotografía.' });
    if (!process.env.OPENAI_API_KEY || process.env.OPENAI_API_KEY === 'replace_me') {
      return res.status(503).json({ error: 'El backend no tiene configurada la clave del proveedor de IA.' });
    }

    const dataUrl = `data:${req.file.mimetype};base64,${req.file.buffer.toString('base64')}`;
    const response = await fetch('https://api.openai.com/v1/chat/completions', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${process.env.OPENAI_API_KEY}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        model: process.env.OPENAI_MODEL || 'gpt-4o-mini',
        temperature: 0.2,
        max_tokens: 650,
        messages: [{
          role: 'user',
          content: [
            { type: 'text', text: prompts[kind] },
            { type: 'image_url', image_url: { url: dataUrl, detail: 'low' } }
          ]
        }]
      })
    });
    const payload = await response.json();
    if (!response.ok) {
      const message = payload?.error?.message || `El proveedor de IA respondió ${response.status}.`;
      return res.status(502).json({ error: message });
    }
    const analysis = payload?.choices?.[0]?.message?.content;
    if (typeof analysis !== 'string' || !analysis.trim()) return res.status(502).json({ error: 'El proveedor no devolvió una respuesta utilizable.' });
    return res.json({ analysis: analysis.trim() });
  } catch (error) {
    console.error('AI analysis error:', error);
    return res.status(500).json({ error: 'No se pudo completar el análisis. Inténtalo de nuevo más tarde.' });
  }
});

app.use((error, _req, res, _next) => {
  if (error instanceof multer.MulterError) return res.status(413).json({ error: 'La imagen supera el límite de 8 MB.' });
  return res.status(400).json({ error: error.message || 'Solicitud no válida.' });
});

app.listen(port, '0.0.0.0', () => console.log(`Glow Up Daily backend listening on ${port}`));
