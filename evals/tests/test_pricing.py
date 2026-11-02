from decimal import Decimal

from labelhub_evals.pricing import price_table


def test_prices_come_from_the_api_application_yml():
    table = price_table()

    assert table["deepseek-v4-flash"].input_cache_miss_per_1m == Decimal("0.14")
    assert table["deepseek-v4-flash"].output_per_1m == Decimal("0.28")
    assert "deepseek-v4-pro" in table


def test_cost_splits_cached_and_uncached_prompt_tokens():
    flash = price_table()["deepseek-v4-flash"]

    # 1M prompt tokens, half cached, plus 1M completion tokens
    cost = flash.cost(prompt_tokens=1_000_000, completion_tokens=1_000_000, cached_prompt_tokens=500_000)

    assert cost == Decimal("0.0014") + Decimal("0.07") + Decimal("0.28")
