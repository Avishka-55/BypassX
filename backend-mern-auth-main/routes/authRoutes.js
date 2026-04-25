import express from 'express'
import rateLimit from 'express-rate-limit'
import { approvalAction, checkAccountStatus, isAuthenticated, login, logout, register, resetPassword, sendRegisterOtp, sendResetOtp, sendVerifyOtp, verifiedEmail } from '../controllers/authController.js';
import userAuth from '../middleware/userAuth.js';
const authRouter = express.Router();

const otpLimiter = rateLimit({
  windowMs: 60 * 60 * 1000, // 1 hour
  limit: 3, // Limit each IP to 3 requests per `window` (here, per hour)
  message: { success: false, message: 'Too many OTP requests from this IP, please try again after an hour' },
});

const registerLimiter = rateLimit({
  windowMs: 60 * 60 * 1000, // 1 hour
  limit: 5, // Limit each IP to 5 registration requests per hour
  message: { success: false, message: 'Too many registration attempts from this IP, please try again after an hour' },
});

authRouter.post('/send-register-otp', otpLimiter, sendRegisterOtp)
authRouter.post('/register', registerLimiter, register)
authRouter.post('/login', login)
authRouter.post('/check-status', checkAccountStatus)
authRouter.get('/approval-action', approvalAction)
authRouter.post('/logout', logout)
authRouter.post('/send-verify-otp', userAuth, sendVerifyOtp)
authRouter.post('/verify-account', userAuth, verifiedEmail)
authRouter.get('/is-auth', userAuth, isAuthenticated)
authRouter.post('/send-reset-otp', sendResetOtp)
authRouter.post('/reset-password', resetPassword)
export default authRouter