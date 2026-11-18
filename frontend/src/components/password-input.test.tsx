import { createRef, type FormEvent } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { PasswordInput } from "@/components/password-input";

describe("PasswordInput", () => {
  it("starts masked and toggles to plain text and back", () => {
    render(
      <>
        <label htmlFor="password">Password</label>
        <PasswordInput id="password" />
      </>,
    );

    const input = screen.getByLabelText("Password") as HTMLInputElement;
    expect(input).toHaveAttribute("type", "password");

    const toggle = screen.getByRole("button", { name: "Show text" });
    expect(toggle).toHaveAttribute("type", "button");
    expect(toggle).toHaveAttribute("aria-pressed", "false");
    expect(toggle).toHaveAttribute("aria-controls", "password");
    expect(toggle).toHaveAttribute("title", "Show password");

    fireEvent.click(toggle);
    expect(input).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "Hide text" })).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(screen.getByRole("button", { name: "Hide text" }));
    expect(input).toHaveAttribute("type", "password");
    expect(screen.getByRole("button", { name: "Show text" })).toHaveAttribute("aria-pressed", "false");
  });

  it("preserves the typed value and the react-hook-form style ref/onChange wiring", () => {
    const ref = createRef<HTMLInputElement>();
    const onChange = vi.fn();
    render(
      <>
        <label htmlFor="newPassword">New Password</label>
        <PasswordInput id="newPassword" ref={ref} onChange={onChange} />
      </>,
    );

    const input = screen.getByLabelText("New Password") as HTMLInputElement;
    fireEvent.change(input, { target: { value: "Correct-Horse-9!" } });
    expect(onChange).toHaveBeenCalledTimes(1);
    expect(ref.current).toBe(input);

    fireEvent.click(screen.getByRole("button", { name: "Show text" }));
    expect(input).toHaveValue("Correct-Horse-9!");
    expect(input).toHaveAttribute("type", "text");
  });

  it("never submits the surrounding form when toggled", () => {
    const onSubmit = vi.fn((event: FormEvent) => event.preventDefault());
    render(
      <form onSubmit={onSubmit}>
        <PasswordInput id="password" />
      </form>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Show text" }));
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("keeps the invalid state on the input", () => {
    render(<PasswordInput id="password" invalid />);
    expect(screen.getByRole("button", { name: "Show text" }).previousElementSibling).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });
});
