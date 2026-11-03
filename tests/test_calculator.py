#!/usr/bin/env python3
"""
HyperCCFit Layout Grid & Dimension Calculator Mathematical Verification Engine.
Tests all Cartesian dimensions, span proportions, and row alignment invariants.
"""

import sys
import time

# --- Reference Implementation of CCFitCalculator ---
MARGIN_DP = 7.0
SLIDER_WIDTH_DP = 64.0
ROW2_HEIGHT_FACTOR = 0.78
GRID_SPAN_COUNT = 30

def calc_dimension_dp(res_name: str, screen_width_dp: float, orientation: str = "portrait", enabled: bool = True) -> float:
    if not enabled or orientation != "portrait":
        return -1.0
    if screen_width_dp <= 380.0:
        return -1.0

    target_panel_width_dp = max(screen_width_dp - 16.0, 342.0)
    col_with_margin = target_panel_width_dp / 4.0

    if res_name in ("control_center_universal_4_rows_with_margin_size", "control_center_panel_width"):
        return target_panel_width_dp
    elif res_name in ("control_center_universal_4_rows_size", "control_center_secondary_panel_content_width"):
        return target_panel_width_dp - (2 * MARGIN_DP)
    elif res_name == "control_center_universal_3_rows_with_margin_size":
        return col_with_margin * 3.0
    elif res_name == "control_center_universal_2_rows_size":
        base_2_rows_dp = (col_with_margin * 2.0) - (2 * MARGIN_DP)
        return base_2_rows_dp * ROW2_HEIGHT_FACTOR
    elif res_name == "control_center_universal_1_row_size":
        return SLIDER_WIDTH_DP
    return -1.0

def get_span_size(item_type: int) -> int:
    if item_type == 2273:
        return 15  # Wi-Fi or Cellular card (15 / 30 spans, 50%)
    elif item_type == 63342:
        return 15  # Media Player (15 / 30 spans, 50%)
    elif item_type in (274442, 865269, 865270):
        return 5   # Sliders: Brightness, Volume, Ringtone (5 / 30 spans, 16.67%)
    elif item_type == 8453:
        return 6   # QS Tile: 5 tiles per row (5 * 6 = 30 spans)
    else:
        return 30  # DeviceCenter, Edit button, headers (Full width 30 spans)

def run_tests():
    print("=" * 75)
    print("HyperCCFit Mathematical Verification Engine (30-Span Grid & Dimens)")
    print("=" * 75)
    start_time = time.time()
    tests_run = 0

    # 1. Test Small Screen Bypass (<= 380dp)
    for width in [240, 320, 360, 375, 380]:
        res = calc_dimension_dp("control_center_panel_width", width)
        assert res == -1.0, f"Expected bypass on small screen {width}dp, got {res}"
        tests_run += 1
    print(f"[PASS] Small screen <= 380dp bypass verified ({tests_run} tests)")

    # 2. Test Landscape Bypass
    for width in [400, 468, 600, 800]:
        res = calc_dimension_dp("control_center_panel_width", width, orientation="landscape")
        assert res == -1.0, f"Expected bypass on landscape screen {width}dp, got {res}"
        tests_run += 1
    print(f"[PASS] Landscape orientation bypass verified")

    # 3. Test High-DPI / Compact Scaling Dimensions
    # Common Android dp widths: 384, 393 (Pixel 7/8/9), 412 (Standard), 440, 468 (HyperOS Small UI), 480, 500, 540, 600
    test_widths = [384, 392, 393, 400, 411, 412, 432, 440, 468, 480, 500, 533, 540, 580, 600]
    for w in test_widths:
        panel_w = calc_dimension_dp("control_center_panel_width", w)
        expected_w = max(w - 16.0, 342.0)
        assert panel_w == expected_w, f"Panel width mismatch for {w}dp: got {panel_w}, expected {expected_w}"
        
        four_rows = calc_dimension_dp("control_center_universal_4_rows_size", w)
        assert four_rows == (expected_w - 14.0), f"4 rows mismatch for {w}dp: got {four_rows}"
        
        three_rows = calc_dimension_dp("control_center_universal_3_rows_with_margin_size", w)
        assert three_rows == (expected_w / 4.0 * 3.0), f"3 rows mismatch for {w}dp: got {three_rows}"
        
        row2_h = calc_dimension_dp("control_center_universal_2_rows_size", w)
        expected_row2_h = ((expected_w / 2.0) - 14.0) * ROW2_HEIGHT_FACTOR
        assert abs(row2_h - expected_row2_h) < 1e-4, f"Row 2 height mismatch for {w}dp: got {row2_h}"
        
        row1_w = calc_dimension_dp("control_center_universal_1_row_size", w)
        assert row1_w == 64.0, f"Row 1 (slider) width mismatch for {w}dp: got {row1_w}"
        
        tests_run += 5

    print(f"[PASS] Dimension calculation equations verified across {len(test_widths)} screen resolutions")

    # 4. Test 30-Span Grid Allocation Completeness & Row Symmetry
    # Row 1: Wi-Fi (2273) + Cellular (2273)
    row1 = [2273, 2273]
    row1_spans = sum(get_span_size(t) for t in row1)
    assert row1_spans == GRID_SPAN_COUNT, f"Row 1 spans must equal {GRID_SPAN_COUNT}, got {row1_spans}"
    tests_run += 1

    # Row 2: Media (63342) + Brightness (274442) + Volume (865269) + Ringer (865270)
    row2 = [63342, 274442, 865269, 865270]
    row2_spans = sum(get_span_size(t) for t in row2)
    assert row2_spans == GRID_SPAN_COUNT, f"Row 2 spans must equal {GRID_SPAN_COUNT}, got {row2_spans}"
    assert get_span_size(63342) == 15, "Media player must occupy exactly 15 spans (50%) to align with Wi-Fi"
    assert sum(get_span_size(t) for t in [274442, 865269, 865270]) == 15, "Triple sliders must together occupy 15 spans"
    tests_run += 3

    # Row 3: Device Center (full width)
    assert get_span_size(99999) == GRID_SPAN_COUNT, "DeviceCenter must occupy 30 spans"
    tests_run += 1

    # Row 4: Circular QS Tiles: 5 tiles per row (6 spans each)
    tiles_row = [8453, 8453, 8453, 8453, 8453]
    tiles_spans = sum(get_span_size(t) for t in tiles_row)
    assert tiles_spans == GRID_SPAN_COUNT, f"5 QS tiles row must equal {GRID_SPAN_COUNT}, got {tiles_spans}"
    for t in tiles_row:
        assert get_span_size(t) == 6, "Each QS tile must occupy 6 spans"
    tests_run += 6

    # 5. Continuous Screen Density Simulation (381dp to 800dp, step 0.5dp = 839 test steps)
    sim_count = 0
    curr_dp = 381.0
    while curr_dp <= 800.0:
        pw = calc_dimension_dp("control_center_panel_width", curr_dp)
        # Margin sanity check: total screen margins = 16dp
        margin = curr_dp - pw
        assert abs(margin - 16.0) < 1e-4, f"Total margins must be exactly 16dp, got {margin} at {curr_dp}dp"
        sim_count += 1
        curr_dp += 0.5
    tests_run += sim_count

    elapsed = time.time() - start_time
    print(f"\nExecution Summary:")
    print(f"- Total Assertions Checked:   {tests_run:,}")
    print(f"- Continuous Density Range:   381.0dp - 800.0dp (step 0.5dp)")
    print(f"- 30-Span Row Invariants:     Row 1 (30), Row 2 (30), QS Tiles (5x6=30) 100% BALANCED")
    print(f"- Verification Speed:         {elapsed:.3f}s ({tests_run/elapsed:,.0f} tests/sec)")
    print(f"\n[VERIFIED] 100% of all {tests_run:,} mathematical layout checks PASSED with 0 errors!")

if __name__ == "__main__":
    run_tests()
