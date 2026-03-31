import express from 'express'
import cors from 'cors'
import dotenv from 'dotenv'
import path from 'path'
import { fileURLToPath } from 'url'
import connectDB from './config/mongodb.js'
import userRouter from './routes/userRoutes.js'
import authRouter from './routes/authRoutes.js'
import cookieParser from 'cookie-parser'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
dotenv.config({ path: path.join(__dirname, '.env') })

const app = express()
const port = process.env.PORT || 4000

// trust reverse proxy (Render)
app.set('trust proxy', 1)

// middleware
app.use(express.json())
app.use(cookieParser())

// Browser clients are checked against allowlist, while native mobile clients
// (no Origin header) are allowed.
const allowedOrigins = (process.env.CORS_ORIGINS || 'http://localhost:5173,https://mern-auth-123.netlify.app')
  .split(',')
  .map(origin => origin.trim())
  .filter(Boolean)

app.use(
  cors({
    origin: (origin, callback) => {
      if (!origin) {
        callback(null, true)
        return
      }

      if (allowedOrigins.includes(origin)) {
        callback(null, true)
        return
      }

      callback(new Error('Not allowed by CORS'))
    },
    credentials: true,
  })
)

app.get('/', (req, res) => {
  res.send('API Working 👌')
})

app.get('/api', (req, res) => {
  res.json({ success: true, message: 'API working' })
})

// routes
app.use('/api/auth', authRouter)
app.use('/api/user', userRouter)

const startServer = async () => {
  try {
    await connectDB()
    app.listen(port, () => console.log(`Server running on ${port}`))
  } catch (error) {
    console.error('Server startup aborted: database unavailable')
    process.exit(1)
  }
}

startServer()
