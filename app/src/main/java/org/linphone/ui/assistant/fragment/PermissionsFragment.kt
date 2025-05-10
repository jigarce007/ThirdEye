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

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantPermissionsFragmentBinding
import org.linphone.ui.GenericFragment

@UiThread
class PermissionsFragment : GenericFragment() {

    private lateinit var binding: AssistantPermissionsFragmentBinding

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        permissions.forEach { (permission, granted) ->
            Log.i("Permission [$permission] is ${if (granted) "granted" else "denied"}")
        }
        if (!allGranted) {
            Log.w("[PermissionsFragment] Not all permissions were granted. Proceeding anyway...")
        }
        goToLoginFragment()
    }

    private val telecomManagerPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.i("[PermissionsFragment] MANAGE_OWN_CALLS is ${if (isGranted) "granted" else "denied"}")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantPermissionsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        binding.setBackClickListener {
            findNavController().popBackStack()
        }

        binding.setSkipClickListener {
            Log.i("[PermissionsFragment] User clicked skip.")
            goToLoginFragment()
        }

        binding.setGrantAllClickListener {
            Log.i("[PermissionsFragment] Requesting all permissions.")
            requestPermissionLauncher.launch(Compatibility.getAllRequiredPermissionsArray())
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.MANAGE_OWN_CALLS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            telecomManagerPermissionLauncher.launch(Manifest.permission.MANAGE_OWN_CALLS)
        }

        if (!Compatibility.hasFullScreenIntentPermission(requireContext())) {
            Compatibility.requestFullScreenIntentPermission(requireContext())
        }
    }

    override fun onResume() {
        super.onResume()
        if (areAllPermissionsGranted()) {
            Log.i("[PermissionsFragment] All permissions granted. Navigating to login.")
            goToLoginFragment()
        }
    }

    private fun areAllPermissionsGranted(): Boolean {
        return Compatibility.getAllRequiredPermissionsArray().all {
            ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
        } && Compatibility.hasFullScreenIntentPermission(requireContext())
    }

    private fun goToLoginFragment() {
        if (findNavController().currentDestination?.id == R.id.permissionsFragment) {
            findNavController().navigate(R.id.action_permissionsFragment_to_thirdPartySipAccountLoginFragment)
        }
    }
}
