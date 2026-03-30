import userModel from '../models/userModel.js';
import { getXuiClientTrafficByEmail, isXuiConfigured } from '../config/xuiClient.js';

export const getUserData = async(req,res)=>{
    try {
        const { id: userId } = req.user;

        const user = await userModel.findById(userId)
        if(!user){
            return res.json({success: false, message: "User not Found"})
        }

        res.json({success: true,
            userData: {
                name: user.name,
                isAccountVerified: user.isAccountVerified,
                subscriptionUrl: user.subscriptionUrl || '',
                xuiSubId: user.xuiSubId || '',
                 
            }
        })
    } catch (error) {
        return res.json({success: false, message: error.message})
    }
}

export const getSubscriptionStatus = async (req, res) => {
    try {
        const { id: userId } = req.user;
        const user = await userModel.findById(userId);
        if (!user) {
            return res.json({ success: false, message: "User not Found" });
        }

        const parseNonNegative = (value, fallback = 0) => {
            const parsed = Number(value);
            return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback;
        };

        let totalBytes = parseNonNegative(user.quotaBytes, 0);
        let usedBytes = 0;
        let expiryAt = parseNonNegative(user.xuiExpiryAt, 0);

        if (user.xuiClientEmail && isXuiConfigured()) {
            try {
                const traffic = await getXuiClientTrafficByEmail(user.xuiClientEmail);
                if (traffic) {
                    if (traffic.total !== undefined && traffic.total !== null && traffic.total !== '') {
                        totalBytes = parseNonNegative(traffic.total, totalBytes);
                    }

                    if (traffic.allTime !== undefined && traffic.allTime !== null && traffic.allTime !== '') {
                        usedBytes = parseNonNegative(traffic.allTime, usedBytes);
                    } else {
                        const up = parseNonNegative(traffic.up, 0);
                        const down = parseNonNegative(traffic.down, 0);
                        usedBytes = up + down;
                    }

                    if (traffic.expiryTime !== undefined && traffic.expiryTime !== null && traffic.expiryTime !== '') {
                        expiryAt = parseNonNegative(traffic.expiryTime, expiryAt);
                    }

                    if (user.quotaBytes !== totalBytes || user.xuiExpiryAt !== expiryAt) {
                        user.quotaBytes = totalBytes;
                        user.xuiExpiryAt = expiryAt;
                        await user.save();
                    }
                }
            } catch (error) {
                // Keep stored values as fallback if 3x-ui is temporarily unavailable.
            }
        }

        const unlimited = totalBytes <= 0;
        const remainingBytes = unlimited ? 0 : Math.max(0, totalBytes - usedBytes);

        return res.json({
            success: true,
            subscription: {
                totalBytes,
                usedBytes,
                remainingBytes,
                expiryAt,
                unlimited,
            }
        });
    } catch (error) {
        return res.json({ success: false, message: error.message });
    }
}