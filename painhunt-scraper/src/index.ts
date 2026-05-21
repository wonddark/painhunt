// src/index.ts
import express from 'express'
import { router } from './api/routes.js'

const app = express()
app.use(express.json({ limit: '5mb' }))
app.use(router)

const port = parseInt(process.env.PORT ?? '3000', 10)
app.listen(port, () => {
  console.log(`painhunt-scraper running on port ${port}`)
})
