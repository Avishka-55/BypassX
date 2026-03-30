import mongoose from 'mongoose'

const connectDB = async () => {
    try {
        mongoose.set('bufferCommands', false)

        const mongoUri = process.env.MONGODB_URL
        if (!mongoUri) {
            throw new Error('MONGODB_URL is not configured')
        }

        mongoose.connection.on('connected', () => {
            console.log('Database connected')
        })

        mongoose.connection.on('error', err => {
            console.log('MongoDB error:', err)
        })

        await mongoose.connect(mongoUri, {
            dbName: process.env.MONGODB_DB_NAME || 'mern-auth',
            serverSelectionTimeoutMS: process.env.NODE_ENV === 'production' ? 30000 : 15000,
            connectTimeoutMS: process.env.NODE_ENV === 'production' ? 30000 : 15000,
        })
    } catch (err) {
        console.error("Failed to connect to MongoDB:", err)
        throw err
    }
}

export default connectDB
