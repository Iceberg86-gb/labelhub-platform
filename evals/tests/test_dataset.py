import pytest

from labelhub_evals.datasets import helpsteer2


@pytest.mark.parametrize(("helpfulness", "correctness", "truth"), [
    (3, 3, "accept"), (4, 4, "accept"),
    (1, 4, "reject"), (4, 1, "reject"), (0, 0, "reject"),
    (2, 4, "borderline"), (4, 2, "borderline"), (2, 2, "borderline"),
])
def test_label_mapping_v1_boundaries(helpfulness, correctness, truth):
    assert helpsteer2.ground_truth(helpfulness, correctness) == truth


def test_committed_smoke_set_is_stratified_unique_and_consistent_with_the_mapping():
    cases = helpsteer2.load_smoke()

    assert len(cases) == 60
    assert len({c.case_id for c in cases}) == 60
    assert {t: sum(c.truth == t for c in cases) for t in ("accept", "borderline", "reject")} == \
        {"accept": 20, "borderline": 20, "reject": 20}
    for case in cases:
        assert case.truth == helpsteer2.ground_truth(case.human_scores["helpfulness"], case.human_scores["correctness"])
