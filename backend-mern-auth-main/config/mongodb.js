import mongoose from 'mongoose'

const connectDB = async () => {
    try {
        mongoose.set('bufferCommands', false)

        mongoose.connection.on('connected', () => {
            console.log('Database connected')
        })

        mongoose.connection.on('error', err => {
            console.log('MongoDB error:', err)
        })

        await mongoose.connect(`${process.env.MONGODB_URL}/mern-auth`, {
            serverSelectionTimeoutMS: 15000,
            connectTimeoutMS: 15000,
        })
    } catch (err) {
        console.error("Failed to connect to MongoDB:", err)
        throw err
    }
}

export default connectDB
