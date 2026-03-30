import mongoose from 'mongoose';

const registerOtpSchema = new mongoose.Schema({
  email: { type: String, required: true, unique: true },
  otp: { type: String, required: true },
  expiresAt: { type: Number, required: true }
});

const registerOtpModel = mongoose.models.registerOtp || mongoose.model('registerOtp', registerOtpSchema);

export default registerOtpModel;