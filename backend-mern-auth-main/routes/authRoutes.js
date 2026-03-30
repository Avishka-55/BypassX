import express from 'express'
import { approvalAction, checkAccountStatus, isAuthenticated, login, logout, register, resetPassword, sendRegisterOtp, sendResetOtp, sendVerifyOtp, verifiedEmail } from '../controllers/authController.js';
import userAuth from '../middleware/userAuth.js';
const authRouter = express.Router();

authRouter.post('/send-register-otp', sendRegisterOtp)
authRouter.post('/register', register)
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