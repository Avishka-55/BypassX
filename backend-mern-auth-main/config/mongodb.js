import mongoose from 'mongoose'

const connectDB = async () => {
    try {
        mongoose.connection.on('connected', () => {
            console.log('Database connected')
        })

        mongoose.connection.on('error', err => {
            console.log('MongoDB error:', err)
        })

        await mongoose.connect(`${process.env.MONGODB_URL}/mern-auth`)
    } catch (err) {
        console.error("Failed to connect to MongoDB:", err)
    }
}

export default connectDB
