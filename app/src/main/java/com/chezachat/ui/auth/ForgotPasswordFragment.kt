package com.chezachat.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.chezachat.R
import com.chezachat.utils.toast

class ForgotPasswordFragment : Fragment() {

    private val viewModel: AuthViewModel by activityViewModels()

    // Step views — we show/hide the three steps in one fragment
    private lateinit var stepEmail: LinearLayout
    private lateinit var stepOtp: LinearLayout
    private lateinit var stepReset: LinearLayout

    private lateinit var etEmail: EditText
    private lateinit var btnSendOtp: Button
    private lateinit var etOtp: EditText
    private lateinit var btnVerifyOtp: Button
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnResetPassword: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvBack: TextView

    private var emailUsed = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_forgot_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stepEmail   = view.findViewById(R.id.stepEmail)
        stepOtp     = view.findViewById(R.id.stepOtp)
        stepReset   = view.findViewById(R.id.stepReset)
        etEmail     = view.findViewById(R.id.etEmail)
        btnSendOtp  = view.findViewById(R.id.btnSendOtp)
        etOtp       = view.findViewById(R.id.etOtp)
        btnVerifyOtp = view.findViewById(R.id.btnVerifyOtp)
        etNewPassword     = view.findViewById(R.id.etNewPassword)
        etConfirmPassword = view.findViewById(R.id.etConfirmPassword)
        btnResetPassword  = view.findViewById(R.id.btnResetPassword)
        progressBar = view.findViewById(R.id.progressBar)
        tvBack      = view.findViewById(R.id.tvBack)

        showStep(1)

        tvBack.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        // Step 1: send OTP to email
        btnSendOtp.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isBlank()) { requireContext().toast("Enter your email"); return@setOnClickListener }
            emailUsed = email
            viewModel.forgotPassword(email)
        }

        // Step 2: verify OTP
        btnVerifyOtp.setOnClickListener {
            val otp = etOtp.text.toString().trim()
            if (otp.length < 4) { requireContext().toast("Enter the OTP code"); return@setOnClickListener }
            viewModel.verifyOtp(emailUsed, otp)
        }

        // Step 3: reset password
        btnResetPassword.setOnClickListener {
            val pw  = etNewPassword.text.toString()
            val pw2 = etConfirmPassword.text.toString()
            if (pw.length < 6)  { requireContext().toast("Password must be at least 6 characters"); return@setOnClickListener }
            if (pw != pw2)      { requireContext().toast("Passwords do not match"); return@setOnClickListener }
            val otp = etOtp.text.toString().trim()
            viewModel.resetPassword(emailUsed, otp, pw)
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            btnSendOtp.isEnabled     = !loading
            btnVerifyOtp.isEnabled   = !loading
            btnResetPassword.isEnabled = !loading
        }

        viewModel.forgotPasswordStep.observe(viewLifecycleOwner) { step ->
            showStep(step)
        }

        viewModel.authResult.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthUiState.Error   -> requireContext().toast(state.message)
                is AuthUiState.Success -> {
                    requireContext().toast("Password reset! Please log in.")
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    private fun showStep(step: Int) {
        stepEmail.visibility = if (step == 1) View.VISIBLE else View.GONE
        stepOtp.visibility   = if (step == 2) View.VISIBLE else View.GONE
        stepReset.visibility = if (step == 3) View.VISIBLE else View.GONE
    }
}
