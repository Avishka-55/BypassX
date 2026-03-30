import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import userModel from '../models/userModel.js';

const BREVO_API_KEY = process.env.BREVO_API_KEY;
const SENDER_EMAIL = process.env.SENDER_EMAIL;
const ADMIN_APPROVAL_EMAIL = process.env.ADMIN_APPROVAL_EMAIL || SENDER_EMAIL;

const buildAuthCookieOptions = () => ({
  httpOnly: true,
  secure: process.env.NODE_ENV === 'production',
  sameSite: process.env.NODE_ENV === 'production' ? 'none' : 'strict',
  maxAge: 7 * 24 * 60 * 60 * 1000,
});

const buildPublicBaseUrl = (req) => {
  if (process.env.BACKEND_PUBLIC_URL) {
    return process.env.BACKEND_PUBLIC_URL.replace(/\/+$/, '');
  }
  return `${req.protocol}://${req.get('host')}`;
};

const createApprovalToken = (userId, nextStatus) => {
  return jwt.sign(
    { id: userId, type: 'approval', status: nextStatus },
    process.env.JWT_SECRET,
    { expiresIn: '7d' }
  );
};

// Helper to send email via Brevo HTTP API
const sendEmail = async ({ toEmail, toName, subject, htmlContent }) => {
  const response = await fetch('https://api.brevo.com/v3/smtp/email', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'api-key': BREVO_API_KEY
    },
    body: JSON.stringify({
      sender: { email: SENDER_EMAIL, name: "Avishka" },
      to: [{ email: toEmail, name: toName }],
      subject,
      htmlContent
    })
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`Email send failed: ${errorText}`);
  }
};

// REGISTER USER
export const register = async (req, res) => {
  const { name, email, password } = req.body;
  if (!name || !email || !password)
    return res.status(400).json({ success: false, message: "Missing Details" });

  try {
    const existingUser = await userModel.findOne({ email });
    if (existingUser)
      return res.status(400).json({ success: false, message: "User already exists" });

    const hashedPassword = await bcrypt.hash(password, 10);

    const user = new userModel({ name, email, password: hashedPassword });
    await user.save();

    const baseUrl = buildPublicBaseUrl(req);
    const approveToken = createApprovalToken(user._id, 'active');
    const rejectToken = createApprovalToken(user._id, 'rejected');
    const approveLink = `${baseUrl}/api/auth/approval-action?token=${encodeURIComponent(approveToken)}`;
    const rejectLink = `${baseUrl}/api/auth/approval-action?token=${encodeURIComponent(rejectToken)}`;

    await sendEmail({
      toEmail: ADMIN_APPROVAL_EMAIL,
      toName: 'BypassX Admin',
      subject: `Approval request: ${user.email}`,
      htmlContent: `<p>A new user is waiting for approval.</p>
      <p><strong>Name:</strong> ${user.name}<br/><strong>Email:</strong> ${user.email}</p>
      <p>
        <a href="${approveLink}" style="padding:10px 14px;background:#1e7e34;color:#fff;text-decoration:none;border-radius:6px;">Approve</a>
        &nbsp;
        <a href="${rejectLink}" style="padding:10px 14px;background:#b02a37;color:#fff;text-decoration:none;border-radius:6px;">Reject</a>
      </p>
      <p>These links expire in 7 days.</p>`
    });

    // Send welcome email
    await sendEmail({
      toEmail: user.email,
      toName: user.name,
      subject: "Registration received",
      htmlContent: `<p>Hi ${user.name},</p>
      <p>Your registration was received and is currently <strong>pending approval</strong>.</p>
      <p>We will notify you once your access is approved.</p>`
    });

    return res.status(201).json({
      success: true,
      status: user.status,
      message: "Your registration is pending approval"
    });

  } catch (error) {
    return res.status(500).json({ success: false, message: error.message });
  }
};

// LOGIN
export const login = async (req, res) => {
  const { email, password } = req.body;
  if (!email || !password)
    return res.json({ success: false, message: 'Email and Password required' });

  try {
    const user = await userModel.findOne({ email });
    if (!user) return res.json({ success: false, message: 'Invalid Email or Password' });

    const accountStatus = user.status || 'pending';
    if (accountStatus !== 'active') {
      const pendingMsg = 'Your registration is pending approval';
      const rejectedMsg = 'Your request was rejected. Contact support for help';
      return res.json({
        success: false,
        status: accountStatus,
        message: accountStatus === 'rejected' ? rejectedMsg : pendingMsg
      });
    }

    const isMatch = await bcrypt.compare(password, user.password);
    if (!isMatch) return res.json({ success: false, message: 'Invalid Email or Password' });

    const token = jwt.sign({ id: user._id }, process.env.JWT_SECRET, { expiresIn: '7d' });
    res.cookie('token', token, buildAuthCookieOptions());

    return res.json({ success: true, message: 'Logged in successfully', token, user: { _id: user._id, name: user.name, email: user.email, isAccountVerified: user.isAccountVerified } });

  } catch (error) {
    return res.json({ success: false, message: error.message });
  }
};

// CHECK ACCOUNT STATUS BY EMAIL
export const checkAccountStatus = async (req, res) => {
  const { email } = req.body;
  if (!email) {
    return res.json({ success: false, message: 'Email is required' });
  }

  try {
    const user = await userModel.findOne({ email });
    if (!user) {
      return res.json({ success: false, message: 'User not found' });
    }

    const status = user.status || 'pending';
    let message = 'Your registration is pending approval';
    if (status === 'active') {
      message = 'Your account is active';
    } else if (status === 'rejected') {
      message = 'Your request was rejected. Contact support for help';
    }

    return res.json({
      success: true,
      status,
      message
    });
  } catch (error) {
    return res.json({ success: false, message: error.message });
  }
};

export const approvalAction = async (req, res) => {
  const { token } = req.query;
  if (!token) {
    return res.status(400).send('<h2>Invalid approval link</h2>');
  }

  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    if (!decoded || decoded.type !== 'approval' || !decoded.id || !decoded.status) {
      return res.status(400).send('<h2>Invalid approval link</h2>');
    }

    const nextStatus = decoded.status === 'active' ? 'active' : 'rejected';
    const user = await userModel.findById(decoded.id);
    if (!user) {
      return res.status(404).send('<h2>User not found</h2>');
    }

    user.status = nextStatus;
    await user.save();

    await sendEmail({
      toEmail: user.email,
      toName: user.name,
      subject: 'Account status update',
      htmlContent: nextStatus === 'active'
        ? '<p>Your BypassX access was approved. You can now log in to the app.</p>'
        : '<p>Your BypassX access request was rejected. Contact support for details.</p>'
    });

    if (nextStatus === 'active') {
      return res.send('<h2>User approved successfully</h2>');
    }
    return res.send('<h2>User rejected successfully</h2>');
  } catch (error) {
    return res.status(400).send('<h2>Approval link expired or invalid</h2>');
  }
};

// LOGOUT
export const logout = (req, res) => {
  try {
    res.clearCookie('token', { httpOnly: true, secure: process.env.NODE_ENV === 'production', sameSite: process.env.NODE_ENV === 'production' ? 'none' : 'strict' });
    return res.json({ success: true, message: 'Logged Out' });
  } catch (error) {
    return res.json({ success: false, message: error.message });
  }
};

// SEND VERIFY OTP
export const sendVerifyOtp = async (req, res) => {
  try {
    const { id: userId } = req.user;
    const user = await userModel.findById(userId);
    if (!user) return res.json({ success: false, message: "User not found" });
    if (user.isAccountVerified) return res.json({ success: false, message: "Account already verified" });

    const otp = String(Math.floor(100000 + Math.random() * 900000));
    user.verifyOtp = otp;
    user.verifyOtpExpireAt = Date.now() + 24 * 60 * 60 * 1000;
    await user.save();

    await sendEmail({
      toEmail: user.email,
      toName: user.name,
      subject: "Account Verification OTP",
      htmlContent: `<p>Your OTP is <strong>${otp}</strong>. It will expire in 24 hours.</p>`
    });

    return res.json({ success: true, message: "Verification OTP sent" });

  } catch (error) {
    return res.json({ success: false, message: error.message });
  }
};

// VERIFY EMAIL
export const verifiedEmail = async (req, res) => {
  const { otp } = req.body;
  const { id: userId } = req.user;

  if (!userId || !otp) return res.json({ success: false, message: "Missing Details" });

  try {
    const user = await userModel.findById(userId);
    if (!user) return res.json({ success: false, message: "User not found" });
    if (user.verifyOtp !== otp) return res.json({ success: false, message: "Invalid OTP" });
    if (user.verifyOtpExpireAt < Date.now()) return res.json({ success: false, message: "OTP expired" });

    user.isAccountVerified = true;
    user.verifyOtp = "";
    user.verifyOtpExpireAt = 0;
    await user.save();

    return res.json({ success: true, message: "Email verified successfully" });
  } catch (error) {
    return res.json({ success: false, message: error.message });
  }
};

// SEND RESET OTP
export const sendResetOtp = async (req, res) => {
  const { email } = req.body;
  if (!email) return res.json({ success: false, message: "Email is required" });

  try {
    const user = await userModel.findOne({ email });
    if (!user) return res.json({ success: false, message: "User not found" });

    const otp = String(Math.floor(100000 + Math.random() * 900000));
    user.resetOtp = otp;
    user.resetOtpExpiredAt = Date.now() + 15 * 60 * 1000;
    await user.save();

    await sendEmail({
      toEmail: user.email,
      toName: user.name,
      subject: "Password Reset OTP",
      htmlContent: `<p>Your OTP is <strong>${otp}</strong>. It will expire in 15 minutes.</p>`
    });

    return res.json({ success: true, message: "Reset OTP sent successfully" });

  } catch (error) {
    return res.json({ success: false, message: error.message });
  }
};

// RESET PASSWORD
export const resetPassword = async (req, res) => {
  const { email, otp, newPassword } = req.body;
  if (!email || !otp || !newPassword) return res.json({ success: false, message: "All fields required" });

  try {
    const user = await userModel.findOne({ email });
    if (!user) return res.json({ success: false, message: "User not found" });
    if (user.resetOtp !== otp) return res.json({ success: false, message: "Invalid OTP" });
    if (user.resetOtpExpiredAt < Date.now()) return res.json({ success: false, message: "OTP expired" });

    user.password = await bcrypt.hash(newPassword, 10);
    user.resetOtp = "";
    user.resetOtpExpiredAt = 0;
    await user.save();

    return res.json({ success: true, message: "Password reset successful" });

  } catch (error) {
    return res.json({ success: false, message: error.message });
  }
};

// CHECK AUTH
export const isAuthenticated = (req, res) => {
  return res.json({ success: true, user: req.user });
};
