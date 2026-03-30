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

        let totalBytes = Math.max(0, Number(user.quotaBytes || 0));
        let usedBytes = 0;
        let expiryAt = Math.max(0, Number(user.xuiExpiryAt || 0));

        if (user.xuiClientEmail && isXuiConfigured()) {
            try {
                const traffic = await getXuiClientTrafficByEmail(user.xuiClientEmail);
                if (traffic) {
                    totalBytes = Math.max(0, Number(traffic.total || totalBytes || 0));
                    usedBytes = Math.max(0, Number(traffic.allTime || (Number(traffic.up || 0) + Number(traffic.down || 0))));
                    if (Number(traffic.expiryTime || 0) > 0) {
                        expiryAt = Number(traffic.expiryTime);
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