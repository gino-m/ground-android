/*
 * Copyright 2018 Google LLC
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

package com.google.android.gnd.ui.home;

import static com.google.android.gnd.rx.RxAutoDispose.autoDisposable;
import static com.google.android.gnd.ui.util.ViewUtil.getScreenHeight;
import static com.google.android.gnd.ui.util.ViewUtil.getScreenWidth;

import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.view.GravityCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import butterknife.BindView;
import com.akaita.java.rxjava2debug.RxJava2Debug;
import com.google.android.gnd.MainActivity;
import com.google.android.gnd.MainViewModel;
import com.google.android.gnd.R;
import com.google.android.gnd.databinding.HomeScreenFragBinding;
import com.google.android.gnd.inject.ActivityScoped;
import com.google.android.gnd.model.Project;
import com.google.android.gnd.model.feature.Point;
import com.google.android.gnd.rx.Loadable;
import com.google.android.gnd.rx.Schedulers;
import com.google.android.gnd.system.AuthenticationManager;
import com.google.android.gnd.ui.common.AbstractFragment;
import com.google.android.gnd.ui.common.BackPressListener;
import com.google.android.gnd.ui.common.BottomSheetBehavior;
import com.google.android.gnd.ui.common.EphemeralPopups;
import com.google.android.gnd.ui.common.ProgressDialogs;
import com.google.android.gnd.ui.common.TwoLineToolbar;
import com.google.android.gnd.ui.home.featuresheet.FeatureSheetViewModel;
import com.google.android.gnd.ui.home.mapcontainer.MapContainerFragment;
import com.google.android.gnd.ui.projectselector.ProjectSelectorDialogFragment;
import com.google.android.gnd.ui.projectselector.ProjectSelectorViewModel;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener;
import io.reactivex.subjects.PublishSubject;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import timber.log.Timber;

/**
 * Fragment containing the map container and feature sheet fragments and NavigationView side drawer.
 * This is the default view in the application, and gets swapped out for other fragments (e.g., view
 * observation and edit observation) at runtime.
 */
@ActivityScoped
public class HomeScreenFragment extends AbstractFragment
    implements BackPressListener, OnNavigationItemSelectedListener, OnGlobalLayoutListener {
  // TODO: It's not obvious which feature are in HomeScreen vs MapContainer; make this more
  // intuitive.
  private static final float COLLAPSED_MAP_ASPECT_RATIO = 3.0f / 2.0f;

  @Inject AddFeatureDialogFragment addFeatureDialogFragment;
  @Inject AuthenticationManager authenticationManager;
  @Inject Schedulers schedulers;

  @BindView(R.id.toolbar_wrapper)
  ViewGroup toolbarWrapper;

  @BindView(R.id.toolbar)
  TwoLineToolbar toolbar;

  @BindView(R.id.status_bar_scrim)
  View statusBarScrim;

  @BindView(R.id.drawer_layout)
  DrawerLayout drawerLayout;

  @BindView(R.id.nav_view)
  NavigationView navView;

  // TODO: Rename variable.
  @BindView(R.id.feature_bottom_sheet)
  View bottomSheetScrollView;

  @BindView(R.id.bottom_sheet_bottom_inset_scrim)
  View bottomSheetBottomInsetScrim;

  @BindView(R.id.version_text)
  TextView versionTextView;

  private ProgressDialog progressDialog;
  private HomeScreenViewModel viewModel;
  private FeatureSheetViewModel featureSheetViewModel;
  private MapContainerFragment mapContainerFragment;
  private BottomSheetBehavior<View> bottomSheetBehavior;
  private PublishSubject<Object> showFeatureDialogRequests;
  private ProjectSelectorDialogFragment projectSelectorDialogFragment;
  private ProjectSelectorViewModel projectSelectorViewModel;
  private List<Project> projects;
  @Nullable private CardView bottomSheetHeader;

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    getViewModel(MainViewModel.class).getWindowInsets().observe(this, this::onApplyWindowInsets);

    viewModel = getViewModel(HomeScreenViewModel.class);
    viewModel.getActiveProject().observe(this, this::onActiveProjectChange);
    viewModel
        .getShowAddFeatureDialogRequests()
        .observe(this, e -> e.ifUnhandled(this::onShowAddFeatureDialogRequest));
    viewModel.getFeatureSheetState().observe(this, this::onFeatureSheetStateChange);
    viewModel.getOpenDrawerRequests().observe(this, e -> e.ifUnhandled(this::openDrawer));

    featureSheetViewModel = getViewModel(FeatureSheetViewModel.class);

    showFeatureDialogRequests = PublishSubject.create();

    showFeatureDialogRequests
        .switchMapMaybe(__ -> addFeatureDialogFragment.show(getChildFragmentManager()))
        .as(autoDisposable(this))
        .subscribe(viewModel::addFeature);

    projectSelectorViewModel = getViewModel(ProjectSelectorViewModel.class);
  }

  @Nullable
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    super.onCreateView(inflater, container, savedInstanceState);

    projectSelectorDialogFragment = new ProjectSelectorDialogFragment();

    HomeScreenFragBinding binding = HomeScreenFragBinding.inflate(inflater, container, false);
    this.bottomSheetHeader = binding.featureSheetHeader.bottomSheetHeader;
    binding.featureSheetChrome.setViewModel(viewModel);
    binding.setViewModel(viewModel);
    binding.setFeatureSheetViewModel(featureSheetViewModel);
    binding.setLifecycleOwner(this);
    return binding.getRoot();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    versionTextView.setText("Build " + getVersionName());
    // Ensure nav drawer cannot be swiped out, which would conflict with map pan gestures.
    drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

    navView.setNavigationItemSelectedListener(this);
    getView().getViewTreeObserver().addOnGlobalLayoutListener(this);

    if (savedInstanceState == null) {
      mapContainerFragment = new MapContainerFragment();
      replaceFragment(R.id.map_container_fragment, mapContainerFragment);
      setUpBottomSheetBehavior();
    } else {
      mapContainerFragment = restoreChildFragment(savedInstanceState, MapContainerFragment.class);
    }
  }

  /** Fetches offline saved projects and adds them to navigation drawer. */
  private void updateNavDrawer() {
    projectSelectorViewModel
        .getOfflineProjects()
        .subscribeOn(schedulers.io())
        .observeOn(schedulers.ui())
        .as(autoDisposable(this))
        .subscribe(this::addProjectToNavDrawer);
  }

  private MenuItem getProjectsNavItem() {
    // Below index is the order of the projects item in nav_drawer_menu.xml
    return navView.getMenu().getItem(1);
  }

  private void addProjectToNavDrawer(List<Project> projects) {
    this.projects = projects;

    // clear last saved projects list
    getProjectsNavItem().getSubMenu().removeGroup(R.id.group_join_project);

    for (int index = 0; index < projects.size(); index++) {
      getProjectsNavItem()
          .getSubMenu()
          .add(R.id.group_join_project, Menu.NONE, index, projects.get(index).getTitle())
          .setIcon(R.drawable.ic_menu_project);
    }

    // Highlight active project
    Loadable.getValue(viewModel.getActiveProject())
        .ifPresent(project -> updateSelectedProjectUI(getSelectedProjectIndex(project)));
  }

  private String getVersionName() {
    try {
      return Objects.requireNonNull(getContext())
          .getPackageManager()
          .getPackageInfo(getContext().getPackageName(), 0)
          .versionName;
    } catch (PackageManager.NameNotFoundException e) {
      return "?";
    }
  }

  @Override
  public void onGlobalLayout() {
    if (toolbarWrapper == null || bottomSheetBehavior == null) {
      return;
    }
    bottomSheetBehavior.setFitToContents(false);

    // Set expanded height so that when the bottom sheet is expanded, the top edge of the header
    // appears directly under the bottom edge of the toolbar.
    int featureSheetMarginTop = (int) getResources().getDimension(R.dimen.feature_sheet_margin_top);
    bottomSheetBehavior.setExpandedOffset(toolbarWrapper.getHeight() - featureSheetMarginTop);

    bottomSheetBehavior.setPeekHeight(getBottomSheetPeekHeight());

    getView().getViewTreeObserver().removeOnGlobalLayoutListener(this);
  }

  private void setUpBottomSheetBehavior() {
    bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetScrollView);
    bottomSheetBehavior.setHideable(true);
    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
    bottomSheetBehavior.setBottomSheetCallback(new BottomSheetCallback());
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    setHasOptionsMenu(true);

    ((MainActivity) getActivity()).setActionBar(toolbar, false);
  }

  private void openDrawer() {
    drawerLayout.openDrawer(GravityCompat.START);
  }

  private void closeDrawer() {
    drawerLayout.closeDrawer(GravityCompat.START);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.feature_sheet_menu, menu);
  }

  @Override
  public void onStart() {
    super.onStart();

    if (viewModel.shouldShowProjectSelectorOnStart()) {
      showProjectSelector();
    }

    viewModel.init();
  }

  @Override
  public void onStop() {
    super.onStop();

    if (projectSelectorDialogFragment.isVisible()) {
      dismissProjectSelector();
    }
  }

  private void showProjectSelector() {
    if (!projectSelectorDialogFragment.isVisible()) {
      projectSelectorDialogFragment.show(
          getFragmentManager(), ProjectSelectorDialogFragment.class.getSimpleName());
    }
  }

  private void dismissProjectSelector() {
    projectSelectorDialogFragment.dismiss();
  }

  private void showOfflineAreas() {
    viewModel.showOfflineAreas();
  }

  private void onApplyWindowInsets(WindowInsetsCompat insets) {
    statusBarScrim.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
    toolbarWrapper.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
    bottomSheetBottomInsetScrim.setMinimumHeight(insets.getSystemWindowInsetBottom());
    updateNavViewInsets(insets);
  }

  private void updateNavViewInsets(WindowInsetsCompat insets) {
    View headerView = navView.getHeaderView(0);
    headerView.setPadding(0, insets.getSystemWindowInsetTop(), 0, 0);
  }

  private int getBottomSheetPeekHeight() {
    double screenWidth = getScreenWidth(getActivity());
    double screenHeight = getScreenHeight(getActivity());
    double collapseMapHeight = screenWidth / COLLAPSED_MAP_ASPECT_RATIO;
    return (int) (screenHeight - collapseMapHeight);
  }

  private void onActiveProjectChange(Loadable<Project> project) {
    switch (project.getState()) {
      case NOT_LOADED:
        dismissLoadingDialog();
        break;
      case LOADED:
        dismissLoadingDialog();
        updateNavDrawer();
        break;
      case LOADING:
        showProjectLoadingDialog();
        break;
      case NOT_FOUND:
      case ERROR:
        project.error().ifPresent(this::onActivateProjectFailure);
        break;
      default:
        Timber.e("Unhandled case: %s", project.getState());
        break;
    }
  }

  private void updateSelectedProjectUI(int selectedIndex) {
    SubMenu subMenu = getProjectsNavItem().getSubMenu();
    for (int i = 0; i < projects.size(); i++) {
      MenuItem menuItem = subMenu.getItem(i);
      menuItem.setChecked(i == selectedIndex);
    }
  }

  private int getSelectedProjectIndex(Project activeProject) {
    for (Project project : projects) {
      if (project.getId().equals(activeProject.getId())) {
        return projects.indexOf(project);
      }
    }
    Timber.e("Selected project not found.");
    return -1;
  }

  private void onShowAddFeatureDialogRequest(Point location) {
    if (!Loadable.getValue(viewModel.getActiveProject()).isPresent()) {
      Timber.e("Attempting to add feature while no project loaded");
      return;
    }
    // TODO: Pause location updates while dialog is open.
    // TODO: Show spinner?
    showFeatureDialogRequests.onNext(new Object());
  }

  private void onFeatureSheetStateChange(FeatureSheetState state) {
    switch (state.getVisibility()) {
      case VISIBLE:
        showBottomSheet();
        break;
      case HIDDEN:
        hideBottomSheet();
        break;
      default:
        Timber.e("Unhandled visibility: %s", state.getVisibility());
        break;
    }
  }

  private void showBottomSheet() {
    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
  }

  private void hideBottomSheet() {
    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
  }

  private void showProjectLoadingDialog() {
    if (progressDialog == null) {
      progressDialog =
          ProgressDialogs.modalSpinner(getContext(), R.string.project_loading_please_wait);
      progressDialog.show();
    }
  }

  public void dismissLoadingDialog() {
    if (progressDialog != null) {
      progressDialog.dismiss();
      progressDialog = null;
    }
  }

  @Override
  public boolean onBack() {
    if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
      return false;
    } else {
      hideBottomSheet();
      return true;
    }
  }

  @Override
  public boolean onNavigationItemSelected(@NonNull MenuItem item) {
    if (item.getGroupId() == R.id.group_join_project) {
      Project selectedProject = projects.get(item.getOrder());
      projectSelectorViewModel.activateOfflineProject(selectedProject.getId());
      closeDrawer();
    } else {
      switch (item.getItemId()) {
        case R.id.nav_join_project:
          showProjectSelector();
          closeDrawer();
          break;
        case R.id.nav_offline_areas:
          showOfflineAreas();
          closeDrawer();
          break;
        case R.id.nav_sign_out:
          authenticationManager.signOut();
          break;
        default:
          Timber.e("Unhandled id: %s", item.getItemId());
          break;
      }
    }
    return false;
  }

  private void onActivateProjectFailure(Throwable throwable) {
    Timber.e(RxJava2Debug.getEnhancedStackTrace(throwable), "Error activating project");
    dismissLoadingDialog();
    EphemeralPopups.showError(getContext(), R.string.project_load_error);
    showProjectSelector();
  }

  // TODO: Move out of HomeScreenFragment.
  private class BottomSheetCallback extends BottomSheetBehavior.BottomSheetCallback {
    private boolean roundedCorners = false;

    @Override
    public void onStateChanged(@NonNull View bottomSheet, int newState) {
      if (newState == BottomSheetBehavior.STATE_HIDDEN) {
        viewModel.onBottomSheetHidden();
      }
    }

    @Override
    public void onSlide(@NonNull View bottomSheet, float slideOffset) {
      if (bottomSheetHeader == null) {
        return;
      }

      if (slideOffset == 1.0) {
        if (roundedCorners) {
          // Remove corners when sheet is fully expanded.
          bottomSheetHeader.setBackgroundResource(R.color.colorBackground);
          roundedCorners = false;
        }
      } else {
        // Restore rounded corners when not full expanded.
        // Note that setting drawable background in layout xml has no effect.
        if (!roundedCorners) {
          bottomSheetHeader.setBackgroundResource(R.drawable.bg_header_card);
          roundedCorners = true;
        }
      }
    }
  }
}
