/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.assistant.fragment

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.annotation.UiThread
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.linphone.R
import org.linphone.databinding.AssistantThirdPartySipAccountLoginFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.GenericFragment
import org.linphone.ui.assistant.viewmodel.ThirdPartySipAccountLoginViewModel
import org.linphone.ui.main.viewmodel.MainViewModel
import org.linphone.utils.UserSession

@UiThread
class ThirdPartySipAccountLoginFragment : GenericFragment() {

    private lateinit var binding: AssistantThirdPartySipAccountLoginFragmentBinding

    private val mainViewModel: MainViewModel by activityViewModels()

    private val viewModel: ThirdPartySipAccountLoginViewModel by navGraphViewModels(
        R.id.assistant_nav_graph
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantThirdPartySipAccountLoginFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel
        binding.backClickListener = View.OnClickListener { goBack() }

        observeToastEvents(viewModel)
        binding.login.setOnClickListener {
            viewModel.login(requireContext())

        }

        // Observe showPassword changes
        viewModel.showPassword.observe(viewLifecycleOwner) {
            lifecycleScope.launch {
                delay(50)
                binding.password.setSelection(binding.password.text?.length ?: 0)
            }
        }

        // Observe accountLoggedInEvent and handle successful login
        viewModel.accountLoggedInEvent.observe(viewLifecycleOwner) {
            UserSession.isLogin = false
            mainViewModel.shouldAutoClick = true
            mainViewModel.userManuallyNavigatedToStartCall = false
            mainViewModel.lastVisibleScreen = "HistoryList" // Or whatever is appropriate
            it.consume {
                requireActivity().finish()
            }
        }

        // Observe accountLoginErrorEvent and show error message
        viewModel.accountLoginErrorEvent.observe(viewLifecycleOwner) {
            it.consume { message ->
                (requireActivity() as GenericActivity).showRedToast(
                    message,
                    R.drawable.warning_circle
                )
            }
        }

        // Observe registrationInProgress to show/hide ProgressBar
        viewModel.registrationInProgress.observe(viewLifecycleOwner) { isInProgress ->
            binding.progressBar?.visibility = if (isInProgress) View.VISIBLE else View.GONE
        }

        val flipAnimator = ObjectAnimator.ofFloat(binding.ivLogoSmall, "rotationY", 0f, 360f).apply {
            duration = 1000L // Duration of one full flip
            interpolator = LinearInterpolator()
            repeatCount = 0 // Only one time
        }
        val flipAnimator1 = ObjectAnimator.ofFloat(binding.ivLogo, "rotationY", 0f, 360f).apply {
            duration = 1000L // Duration of one full flip
            interpolator = LinearInterpolator()
            repeatCount = 0 // Only one time
        }
        flipAnimator1.start()
        flipAnimator.start()

    }

    private fun goBack() {
        findNavController().popBackStack()
    }
}
