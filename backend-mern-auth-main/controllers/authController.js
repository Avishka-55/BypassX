import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import userModel from '../models/userModel.js';

const BREVO_API_KEY = process.env.BREVO_API_KEY;
const SENDER_EMAIL = process.env.SENDER_EMAIL;
const SENDER_NAME = 'Avishka';
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

const escapeHtml = (value = '') => String(value)
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;');

const buildEmailLayout = ({ preheader, title, intro, bodyHtml, footerNote = 'If you did not expect this email, you can ignore it.' }) => `
<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>${escapeHtml(title)}</title>
</head>
<body style="margin:0;padding:0;background:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#10243a;">
  <span style="display:none!important;visibility:hidden;opacity:0;height:0;width:0;overflow:hidden;">${escapeHtml(preheader)}</span>
  <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%" style="background:#f4f7fb;padding:24px 12px;">
    <tr>
      <td align="center">
        <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="640" style="max-width:640px;background:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #d8e2ee;">
          <tr>
            <td style="background:linear-gradient(135deg,#0f4c81,#0b9ecf);padding:18px 24px;color:#ffffff;">
              <div style="font-size:22px;font-weight:700;letter-spacing:0.2px;">BypassX</div>
              <div style="font-size:12px;opacity:0.9;margin-top:3px;">Secure access updates</div>
            </td>
          </tr>
          <tr>
            <td style="padding:26px 24px 20px 24px;">
              <h1 style="margin:0 0 10px 0;font-size:24px;line-height:1.3;color:#0f2741;">${escapeHtml(title)}</h1>
              <p style="margin:0 0 14px 0;font-size:14px;line-height:1.6;color:#324b66;">${escapeHtml(intro)}</p>
              <div style="font-size:14px;line-height:1.7;color:#17324f;">${bodyHtml}</div>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 24px;background:#f8fbff;border-top:1px solid #e4edf7;font-size:12px;line-height:1.6;color:#526b84;">
              <div>${escapeHtml(footerNote)}</div>
              <div style="margin-top:6px;">- ${escapeHtml(SENDER_NAME)}</div>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>`;

const buildAdminApprovalHtml = ({ name, email, approveLink, rejectLink }) => buildEmailLayout({
  preheader: 'New BypassX registration requires your review',
  title: 'New Registration Request',
  intro: 'A user has signed up and is waiting for your approval decision.',
  bodyHtml: `
    <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%" style="background:#f7fafd;border:1px solid #deebf7;border-radius:10px;padding:12px 14px;margin:6px 0 16px 0;">
      <tr><td style="font-size:13px;color:#4a6178;">Name</td><td style="font-size:14px;font-weight:600;color:#0f2741;">${escapeHtml(name)}</td></tr>
      <tr><td style="font-size:13px;color:#4a6178;">Email</td><td style="font-size:14px;font-weight:600;color:#0f2741;">${escapeHtml(email)}</td></tr>
    </table>
    <p style="margin:0 0 12px 0;">Choose an action below:</p>
    <a href="${approveLink}" style="display:inline-block;padding:10px 16px;background:#1d8f44;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:700;">Approve</a>
    <a href="${rejectLink}" style="display:inline-block;padding:10px 16px;background:#c3383f;color:#ffffff;text-decoration:none;border-radius:8px;font-weight:700;margin-left:8px;">Reject</a>
    <p style="margin:14px 0 0 0;color:#5f7388;font-size:12px;">Action links expire in 7 days.</p>`,
  footerNote: 'This is an admin approval notification from BypassX.'
});

const buildUserPendingHtml = ({ name }) => buildEmailLayout({
  preheader: 'Your BypassX registration is pending approval',
  title: 'Registration Received',
  intro: `Hi ${name}, we have received your registration request.`,
  bodyHtml: `
    <p style="margin:0 0 10px 0;">Your account is currently in <strong>Pending Approval</strong> status.</p>
    <p style="margin:0;">You will receive another email as soon as your request is approved or rejected.</p>`,
  footerNote: 'Need help? Contact support if you did not request this registration.'
});

const buildStatusUpdateHtml = ({ approved }) => buildEmailLayout({
  preheader: approved ? 'Your BypassX account has been approved' : 'Your BypassX request was rejected',
  title: approved ? 'Access Approved' : 'Access Request Update',
  intro: approved
    ? 'Good news. Your BypassX account is now active.'
    : 'Your BypassX access request could not be approved at this time.',
  bodyHtml: approved
    ? '<p style="margin:0;">You can now open the app and log in with your registered credentials.</p>'
    : '<p style="margin:0;">Please contact support if you believe this was a mistake or need further details.</p>',
  footerNote: approved
    ? 'Welcome to BypassX.'
    : 'This decision notification was sent by BypassX.'
});

// Helper to send email via Brevo HTTP API
const sendEmail = async ({ toEmail, toName, subject, htmlContent }) => {
  const response = await fetch('https://api.brevo.com/v3/smtp/email', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'api-key': BREVO_API_KEY
    },
    body: JSON.stringify({
      sender: { email: SENDER_EMAIL, name: SENDER_NAME },
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
      htmlContent: buildAdminApprovalHtml({
        name: user.name,
        email: user.email,
        approveLink,
        rejectLink
      })
    });

    // Send welcome email
    await sendEmail({
      toEmail: user.email,
      toName: user.name,
      subject: "Registration received",
      htmlContent: buildUserPendingHtml({ name: user.name })
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
    return res.json({ success: false, status: 'invalid', message: 'Email is required' });
  }

  try {
    const user = await userModel.findOne({ email });
    if (!user) {
      return res.json({
        success: false,
        status: 'not_found',
        message: 'Account no longer exists. Please register again.'
      });
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
    return res.json({ success: false, status: 'error', message: 'Unable to check account status' });
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
      htmlContent: buildStatusUpdateHtml({ approved: nextStatus === 'active' })
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
