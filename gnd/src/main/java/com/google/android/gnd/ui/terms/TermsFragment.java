/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.gnd.ui.terms;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.Nullable;
import com.google.android.gnd.databinding.FragmentTermsBinding;
import com.google.android.gnd.model.Terms;
import com.google.android.gnd.rx.Loadable;
import com.google.android.gnd.ui.common.AbstractFragment;
import com.google.android.gnd.ui.common.BackPressListener;
import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

@AndroidEntryPoint
public class TermsFragment extends AbstractFragment implements BackPressListener {

  private TermsViewModel viewModel;
  @SuppressWarnings("NullAway")
  private FragmentTermsBinding binding;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    this.viewModel = getViewModel(TermsViewModel.class);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    binding = FragmentTermsBinding.inflate(inflater, container, false);
    binding.setViewModel(viewModel);
    binding.setLifecycleOwner(this);
    viewModel.getTerms().observe(getViewLifecycleOwner(), this::getProjectTerms);
    return binding.getRoot();
  }

  private void getProjectTerms(Loadable<Terms> projectTerms) {
    switch (projectTerms.getState()) {
      case LOADING:
        Timber.i("Loading terms");
        break;
      case LOADED:
        binding.termsLoadingProgressBar.setVisibility(View.GONE);
        binding.termsText.setVisibility(View.VISIBLE);
        viewModel.setTermsTextView(projectTerms.value().get().getTerms());
        break;
      case NOT_FOUND:
      case ERROR:
        binding.termsLoadingProgressBar.setVisibility(View.GONE);
        binding.termsText.setVisibility(View.VISIBLE);
        viewModel.setTermsTextView("Some issue");
        break;
      default:
        Timber.e("Unhandled state: %s",  projectTerms.getState());
        break;
    }
  }

  @Override
  public boolean onBack() {
    getActivity().finish();
    return false;
  }
}
