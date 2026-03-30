import express from 'express'
import userAuth from '../middleware/userAuth.js';
import { getSubscriptionStatus, getUserData } from '../controllers/userController.js';

const userRouter = express.Router();
userRouter.get('/data', userAuth, getUserData)
userRouter.get('/subscription-status', userAuth, getSubscriptionStatus)

export default userRouter